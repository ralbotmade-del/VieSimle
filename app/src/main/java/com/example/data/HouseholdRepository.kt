package com.example.data

import android.content.Context
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.Household
import com.example.model.HouseholdActivity
import com.example.model.HouseholdMember
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

class HouseholdRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Utilisateur non connecté.")
    }

    private fun handleStreamError(error: Throwable, path: String, uid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }

    fun observeHouseholds(userId: String): Flow<List<Household>> = flow {
        val path = "households"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap -> snap.toObjects(Household::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleStreamError(error, path, userId)
                    throw error
                }
        )
    }

    fun observeHouseholdMembers(householdId: String, userId: String): Flow<List<HouseholdMember>> = flow {
        if (householdId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "households/$householdId/members"
        emitAll(
            db.collection("households").document(householdId).collection("members")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snap -> snap.toObjects(HouseholdMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleStreamError(error, path, userId)
                    throw error
                }
        )
    }

    fun observeHouseholdActivities(userId: String): Flow<List<HouseholdActivity>> = flow {
        val path = "household_activities"
        emitAll(
            db.collection(path)
                .whereArrayContains("sharedWithUids", userId)
                .snapshots()
                .map { snap -> snap.toObjects(HouseholdActivity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleStreamError(error, path, userId)
                    throw error
                }
        )
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return "VS" + (1..6).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun createHousehold(name: String, ownerDisplayName: String): Result<String> = try {
        val uid = requireUserId()
        val hhRef = db.collection("households").document()
        val inviteCode = generateInviteCode()
        val safeName = name.trim().take(80).ifBlank { "Mon foyer" }
        val safeOwnerName = ownerDisplayName.trim().take(80).ifBlank { "Propriétaire" }

        hhRef.set(
            mapOf(
                "ownerId" to uid,
                "name" to safeName,
                "inviteCode" to inviteCode,
                "memberUids" to listOf(uid),
                "adminUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("household_invites").document(inviteCode).set(
            mapOf(
                "householdId" to hhRef.id,
                "householdName" to safeName,
                "ownerId" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()

        hhRef.collection("members").document(uid).set(
            mapOf(
                "userId" to uid,
                "householdId" to hhRef.id,
                "displayName" to safeOwnerName,
                "role" to "owner",
                "status" to "active",
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("user_profiles").document(uid).update(
            mapOf("activeHouseholdId" to hhRef.id, "updatedAt" to FieldValue.serverTimestamp())
        ).await()

        postActivity(hhRef.id, listOf(uid), safeOwnerName, "$safeOwnerName a créé le foyer « $safeName ».", "CREATE_HOUSEHOLD")
        Result.success(hhRef.id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.CREATE, "households")
        Result.failure(e)
    }

    suspend fun joinHouseholdByCode(rawCode: String, memberDisplayName: String): Result<String> = try {
        val uid = requireUserId()
        val code = rawCode.trim().uppercase()
        require(code.length in 6..16) { "Code d’invitation invalide." }
        val inviteSnap = db.collection("household_invites").document(code).get().await()
        if (!inviteSnap.exists()) {
            return Result.failure(IllegalArgumentException("Aucun foyer trouvé pour ce code d’invitation."))
        }
        val householdId = inviteSnap.getString("householdId").orEmpty()
        require(householdId.isNotBlank())

        db.collection("households").document(householdId).update(
            mapOf(
                "memberUids" to FieldValue.arrayUnion(uid),
                "lastInviteCode" to code,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        val hhSnap = db.collection("households").document(householdId).get().await()
        val memberUids = (hhSnap.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: listOf(uid)
        val safeName = memberDisplayName.trim().take(80).ifBlank { "Membre" }

        db.collection("households").document(householdId).collection("members").document(uid).set(
            mapOf(
                "userId" to uid,
                "householdId" to householdId,
                "displayName" to safeName,
                "role" to "member",
                "status" to "active",
                "memberUids" to memberUids.take(10),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        // Sync memberUids across existing member records so everyone sees the new member
        val membersSnap = db.collection("households").document(householdId).collection("members")
            .whereArrayContains("memberUids", uid).get().await()
        // Also fetch member docs owned by existing members if we are admin later; for now update profile
        db.collection("user_profiles").document(uid).update(
            mapOf("activeHouseholdId" to householdId, "updatedAt" to FieldValue.serverTimestamp())
        ).await()

        postActivity(householdId, memberUids, safeName, "$safeName a rejoint le foyer.", "JOIN_HOUSEHOLD")
        Result.success(householdId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "households/join")
        Result.failure(e)
    }

    suspend fun updateMemberRole(household: Household, memberUid: String, newRole: String): Result<Unit> = try {
        requireUserId()
        require(memberUid != household.ownerId) { "Le propriétaire ne peut pas changer son rôle." }
        val nextAdmins = if (newRole == "admin") {
            (household.adminUids + memberUid).distinct().take(10)
        } else {
            household.adminUids.filter { it != memberUid || it == household.ownerId }
        }
        db.collection("households").document(household.id).update(
            mapOf("adminUids" to nextAdmins, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        db.collection("households").document(household.id).collection("members").document(memberUid).update(
            mapOf(
                "role" to newRole,
                "memberUids" to household.memberUids,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "households/${household.id}/members/$memberUid")
        Result.failure(e)
    }

    suspend fun removeMemberFromHousehold(household: Household, memberUid: String): Result<Unit> = try {
        requireUserId()
        require(memberUid != household.ownerId) { "Impossible de retirer le propriétaire." }
        val nextMembers = household.memberUids.filter { it != memberUid }
        val nextAdmins = household.adminUids.filter { it != memberUid }
        db.collection("households").document(household.id).update(
            mapOf(
                "memberUids" to nextMembers,
                "adminUids" to nextAdmins,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        db.collection("households").document(household.id).collection("members").document(memberUid).delete().await()
        // Rule: removing a member never deletes their private documents.
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "households/${household.id}/remove/$memberUid")
        Result.failure(e)
    }

    suspend fun leaveHousehold(household: Household): Result<Unit> = try {
        val uid = requireUserId()
        require(uid != household.ownerId) { "Le propriétaire doit supprimer le foyer ou rester propriétaire." }
        val nextMembers = household.memberUids.filter { it != uid }
        val nextAdmins = household.adminUids.filter { it != uid }
        db.collection("households").document(household.id).update(
            mapOf(
                "memberUids" to nextMembers,
                "adminUids" to nextAdmins,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        db.collection("households").document(household.id).collection("members").document(uid).delete().await()
        db.collection("user_profiles").document(uid).update(
            mapOf("activeHouseholdId" to "", "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "households/${household.id}/leave")
        Result.failure(e)
    }

    suspend fun deleteHousehold(household: Household): Result<Unit> = try {
        val uid = requireUserId()
        check(household.ownerId == uid)
        if (household.inviteCode.isNotBlank()) {
            try {
                db.collection("household_invites").document(household.inviteCode).delete().await()
            } catch (_: Exception) { }
        }
        val membersSnap = db.collection("households").document(household.id).collection("members")
            .whereArrayContains("memberUids", uid).get().await()
        for (m in membersSnap.documents) {
            m.reference.delete().await()
        }
        db.collection("households").document(household.id).delete().await()
        db.collection("user_profiles").document(uid).update(
            mapOf("activeHouseholdId" to "", "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "households/${household.id}")
        Result.failure(e)
    }

    suspend fun postActivity(
        householdId: String,
        sharedWithUids: List<String>,
        actorName: String,
        message: String,
        actionType: String
    ) {
        if (householdId.isBlank()) return
        try {
            val uid = requireUserId()
            db.collection("household_activities").document().set(
                mapOf(
                    "userId" to uid,
                    "actorName" to actorName.take(80).ifBlank { "Membre" },
                    "householdId" to householdId,
                    "sharedWithUids" to (sharedWithUids + uid).distinct().take(10),
                    "message" to message.take(200),
                    "actionType" to actionType.take(40),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "household_activities")
        }
    }

    suspend fun exportUserDataToJsonFile(context: Context): Result<File> = try {
        val uid = requireUserId()
        val profile = db.collection("user_profiles").document(uid).get().await().data
        val docs = db.collection("vault_documents").whereArrayContains("sharedWithUids", uid).get().await().documents.map { it.data }
        val deadlines = db.collection("deadlines").whereArrayContains("sharedWithUids", uid).get().await().documents.map { it.data }
        val subs = db.collection("subscriptions").whereArrayContains("sharedWithUids", uid).get().await().documents.map { it.data }
        val tasks = db.collection("tasks").whereArrayContains("sharedWithUids", uid).get().await().documents.map { it.data }

        val root = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("userId", uid)
            put("profile", JSONObject(profile ?: emptyMap<String, Any>()))
            put("documents", JSONArray(docs.map { JSONObject(it ?: emptyMap<String, Any>()) }))
            put("deadlines", JSONArray(deadlines.map { JSONObject(it ?: emptyMap<String, Any>()) }))
            put("subscriptions", JSONArray(subs.map { JSONObject(it ?: emptyMap<String, Any>()) }))
            put("tasks", JSONArray(tasks.map { JSONObject(it ?: emptyMap<String, Any>()) }))
        }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, "viesimple_export_${System.currentTimeMillis()}.json")
        outFile.writeText(root.toString(2))
        Result.success(outFile)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "export/$e")
        Result.failure(e)
    }

    suspend fun deleteAllOwnedUserData(context: Context): Result<Unit> = try {
        val uid = requireUserId()
        val collections = listOf("vault_documents", "deadlines", "subscriptions", "tasks")
        for (col in collections) {
            val snap = db.collection(col).whereArrayContains("sharedWithUids", uid).get().await()
            for (doc in snap.documents) {
                if (doc.getString("userId") == uid) {
                    doc.reference.delete().await()
                }
            }
        }
        val secLogs = db.collection("users").document(uid).collection("security_logs").get().await()
        secLogs.documents.forEach { it.reference.delete().await() }
        val aiMsgs = db.collection("users").document(uid).collection("ai_conversations").get().await()
        aiMsgs.documents.forEach { it.reference.delete().await() }
        val fcmTokens = db.collection("users").document(uid).collection("fcm_tokens").get().await()
        fcmTokens.documents.forEach { it.reference.delete().await() }
        db.collection("user_profiles").document(uid).delete().await()

        // Wipe local encrypted vault files
        File(context.filesDir, "vault").deleteRecursively()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, "users/delete_all")
        Result.failure(e)
    }
}
