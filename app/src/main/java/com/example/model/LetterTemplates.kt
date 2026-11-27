package com.example.model

data class LetterTemplate(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val deliveryHint: String,
    val supportsAmount: Boolean = false,
    val subject: String,
    val bodyTemplate: String
)

data class LetterFillData(
    val senderName: String = "",
    val senderEmail: String = "",
    val senderAddress: String = "",
    val senderCity: String = "",
    val senderPhone: String = "",
    val orgName: String = "",
    val orgAddress: String = "",
    val contractName: String = "",
    val referenceNumber: String = "",
    val amountText: String = "",
    val reasonText: String = "",
    val dateLabel: String = DateUtils.todayNumeric()
)

object LetterTemplates {
    val ALL = listOf(
        LetterTemplate(
            id = "resiliation_abonnement",
            title = "Résiliation d’abonnement",
            category = "Abonnements",
            description = "Mettre fin à un abonnement (streaming, téléphonie, fibre, sport, logiciel).",
            deliveryHint = "E-mail ou courrier simple / RAR",
            supportsAmount = true,
            subject = "Demande de résiliation de mon abonnement n° [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Par la présente, je vous informe de ma volonté de résilier mon abonnement [NOM_CONTRAT] souscrit sous la référence [REFERENCE].

Conformément aux conditions générales applicables et aux dispositions légales en vigueur, je vous remercie de prendre en compte cette demande dès réception et de stopper tout prélèvement automatique à l’issue de la période en cours.

Je vous prie de bien vouloir m’adresser une confirmation écrite par e-mail ([EMAIL_EXPEDITEUR]) ou par courrier précisant la date effective de clôture de mon contrat.

Dans cette attente, je vous prie d’agréer, Madame, Monsieur, l’expression de mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "resiliation_assurance",
            title = "Résiliation d’assurance",
            category = "Assurance",
            description = "Résilier un contrat auto, habitation ou santé à échéance ou via la loi Hamon.",
            deliveryHint = "Recommandé avec AR conseillé",
            supportsAmount = true,
            subject = "Résiliation du contrat d’assurance n° [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Je vous informe par la présente de ma décision de résilier le contrat d’assurance [NOM_CONTRAT] référencé sous le numéro [REFERENCE], conformément aux dispositions légales applicables (échéance annuelle, loi Chatel ou loi Hamon).

Je vous remercie de bien vouloir me confirmer la date de prise d’effet de cette résiliation, de m’adresser l’avis de clôture (ou relevé d’information) correspondant ainsi que le remboursement de l’éventuel trop-perçu de cotisation.

Veuillez agréer, Madame, Monsieur, l’expression de mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "preavis_logement",
            title = "Préavis de départ logement",
            category = "Logement",
            description = "Donner congé de votre bail d’habitation auprès de votre propriétaire ou agence.",
            deliveryHint = "Recommandé avec AR obligatoire",
            supportsAmount = true,
            subject = "Congé du logement — Bail n° [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Locataire du logement concerné par le bail [NOM_CONTRAT] (réf. : [REFERENCE]), je vous informe par la présente de ma décision de quitter les lieux.

Conformément aux dispositions de la loi du 6 juillet 1989, ce congé prendra effet à l’issue du délai légal de préavis à compter de la réception de ce courrier ([MOTIF_PRECIS]).

Je reste à votre disposition afin de convenir ensemble d’une date pour l’état des lieux de sortie et la remise des clés, ainsi que pour la restitution du dépôt de garantie.

Je vous prie d’agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "changement_adresse",
            title = "Changement d’adresse",
            category = "Logement",
            description = "Notifier votre nouvelle adresse postale à un organisme ou prestataire.",
            deliveryHint = "E-mail ou courrier simple",
            subject = "Notification de changement d’adresse — Dossier [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Je vous informe que j’ai changé d’adresse postale. À compter du [DATE_JOUR], merci d’adresser toute correspondance relative à mon dossier [NOM_CONTRAT] (réf. : [REFERENCE]) à ma nouvelle adresse :

[ADRESSE_EXPEDITEUR]

Je vous remercie de bien vouloir mettre à jour mes coordonnées dans vos fichiers et de me confirmer la prise en compte de cette modification.

Veuillez agréer, Madame, Monsieur, l’expression de mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "contestation_facture",
            title = "Contestation de facture",
            category = "Banque",
            description = "Contester un montant facturé, un double débit ou une erreur de facturation.",
            deliveryHint = "Recommandé avec AR conseillé",
            supportsAmount = true,
            subject = "Contestation de la facture n° [REFERENCE] ([MONTANT])",
            bodyTemplate = """Madame, Monsieur,

Je me permets de contester la facture référencée [REFERENCE] relative à [NOM_CONTRAT] d’un montant de [MONTANT], pour le motif suivant : [MOTIF_PRECIS].

En conséquence, je vous remercie de bien vouloir procéder à la vérification de ce dossier, de suspendre tout prélèvement contesté et de m’adresser un avoir ou une facture rectifiée dans les meilleurs délais.

Vous trouverez ci-joint les pièces justificatives utiles à l’examen de ma demande.

Veuillez agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "demande_remboursement",
            title = "Demande de remboursement",
            category = "Banque",
            description = "Réclamer le remboursement d’un trop-perçu, d’une caution ou d’un achat annulé.",
            deliveryHint = "E-mail ou recommandé AR",
            supportsAmount = true,
            subject = "Demande de remboursement de [MONTANT] — Réf. [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Suite à [MOTIF_PRECIS], je vous demande de bien vouloir procéder au remboursement de la somme de [MONTANT] relative au dossier ou contrat [NOM_CONTRAT] (réf. : [REFERENCE]).

Je vous remercie d’effectuer ce versement sur le moyen de paiement utilisé initialement (ou par virement sur mon compte bancaire) et de m’en confirmer l’exécution par écrit à [EMAIL_EXPEDITEUR].

Veuillez agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "opposition_prelevement",
            title = "Opposition à un prélèvement",
            category = "Banque",
            description = "Demander à votre banque la révocation définitive d’un mandat SEPA.",
            deliveryHint = "Messagerie bancaire ou courrier signé",
            subject = "Révocation de mandat de prélèvement SEPA — [ORGANISME]",
            bodyTemplate = """Madame, Monsieur,

Je vous demande par la présente de bien vouloir révoquer définitivement l’autorisation de prélèvement SEPA accordée au créancier [ORGANISME] concernant [NOM_CONTRAT] (Référence Unique de Mandat : [REFERENCE]).

Je vous remercie de bloquer tout futur prélèvement émanant de cet émetteur à compter de la réception de ce courrier et de me confirmer la prise en compte de cette instruction.

Veuillez agréer, Madame, Monsieur, l’expression de mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "demande_delai_impots",
            title = "Demande de délai de paiement (Impôts)",
            category = "Impôts",
            description = "Solliciter un échelonnement ou une remise gracieuse auprès des finances publiques.",
            deliveryHint = "Messagerie sécurisée impots.gouv ou courrier",
            supportsAmount = true,
            subject = "Demande d’échelonnement de paiement — Avis n° [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Redevable d’une somme de [MONTANT] au titre de [NOM_CONTRAT] (référence fiscale : [REFERENCE]), je me permets de solliciter un aménagement de paiement.

En effet, en raison de [MOTIF_PRECIS], je rencontre des difficultés temporaires pour régler cette somme en une seule fois et souhaiterais pouvoir m’en acquitter selon un échéancier adapté à ma situation.

Vous remerciant par avance de l’attention bienveillante que vous porterez à cette demande, je vous prie d’agréer, Madame, Monsieur, l’expression de ma considération distinguée.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "demande_remboursement_sante",
            title = "Remboursement santé / mutuelle",
            category = "Santé",
            description = "Demander la prise en charge ou le remboursement d’un acte ou devis médical.",
            deliveryHint = "Espace adhérent ou courrier",
            supportsAmount = true,
            subject = "Demande de remboursement santé — Adhérent n° [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Adhérent(e) à votre organisme de santé sous le numéro [REFERENCE] ([NOM_CONTRAT]), je vous adresse ci-joint les justificatifs de soins acquittés pour un montant de [MONTANT] ([MOTIF_PRECIS]).

Je vous remercie de bien vouloir procéder au règlement de la part complémentaire sur mon compte bancaire habituel et de me transmettre le décompte correspondant par e-mail à [EMAIL_EXPEDITEUR].

Veuillez agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "demande_attestation",
            title = "Demande d’attestation",
            category = "Assurance",
            description = "Obtenir une attestation officielle (assurance, domicile, scolarité, garantie).",
            deliveryHint = "E-mail ou courrier simple",
            subject = "Demande d’attestation — Dossier n° [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Je vous remercie de bien vouloir m’établir et me transmettre une attestation à jour concernant [NOM_CONTRAT] (référence : [REFERENCE]).

Ce document m’est nécessaire dans le cadre de mes démarches administratives ([MOTIF_PRECIS]). Je vous remercie de me le faire parvenir par e-mail à [EMAIL_EXPEDITEUR] ou par courrier postal à l’adresse indiquée en en-tête.

Je vous prie d’agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "demande_attestation_employeur",
            title = "Attestation employeur / salaire",
            category = "Travail",
            description = "Demander à son employeur ou service RH une attestation ou copie de bulletin.",
            deliveryHint = "E-mail RH ou remise en main propre",
            subject = "Demande d’attestation professionnelle — Réf. [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Dans le cadre de mes démarches personnelles ([MOTIF_PRECIS]), je vous serais reconnaissant(e) de bien vouloir me délivrer une attestation employeur / de salaire relative à mon contrat [NOM_CONTRAT] (matricule / réf. : [REFERENCE]).

Vous pouvez me la transmettre par voie électronique à l’adresse [EMAIL_EXPEDITEUR] ou en version papier.

En vous remerciant par avance, je vous prie d’agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "reclamation",
            title = "Réclamation client / service",
            category = "Administratif",
            description = "Signaler un dysfonctionnement, retard de livraison ou manquement contractuel.",
            deliveryHint = "Recommandé avec AR conseillé",
            subject = "Réclamation concernant le dossier n° [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Client(e) de votre organisme pour [NOM_CONTRAT] sous la référence [REFERENCE], je me permets de vous contacter au sujet de [MOTIF_PRECIS].

Malgré mes précédentes démarches, la situation demeure non résolue à ce jour. Je vous saurais gré de bien vouloir réexaminer mon dossier et de m’apporter une réponse précise sous un délai de 15 jours.

Vous trouverez ci-joint les pièces justificatives utiles au traitement de cette demande.

Je vous prie d’agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "duplicata",
            title = "Demande de duplicata",
            category = "Administratif",
            description = "Obtenir une copie d’une facture, contrat, quittance ou diplôme.",
            deliveryHint = "E-mail ou courrier simple",
            subject = "Demande de duplicata — Réf. [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Je vous serais reconnaissant(e) de bien vouloir me transmettre un duplicata du document relatif à [NOM_CONTRAT] (référence : [REFERENCE]).

Vous pouvez me faire parvenir cette copie par voie électronique à l’adresse [EMAIL_EXPEDITEUR] ou par courrier postal à l’adresse indiquée ci-dessus.

En vous remerciant par avance de votre diligence, je vous prie d’agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "demande_regularisation",
            title = "Demande de régularisation",
            category = "Administratif",
            description = "Demander la mise en conformité ou l’ajustement d’un dossier administratif.",
            deliveryHint = "E-mail ou courrier suivi",
            subject = "Demande de régularisation de dossier — Réf. [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Je vous adresse la présente afin de solliciter la régularisation de mon dossier [NOM_CONTRAT] référencé [REFERENCE] concernant [MOTIF_PRECIS].

Vous trouverez ci-joint les pièces justificatives nécessaires à la mise à jour de ma situation. Je vous remercie de bien vouloir me confirmer la régularisation effective de mon dossier.

Je vous prie d’agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        ),
        LetterTemplate(
            id = "demande_rdv",
            title = "Demande de rendez-vous",
            category = "Administratif",
            description = "Solliciter un entretien administratif, bancaire ou médical.",
            deliveryHint = "E-mail ou courrier simple",
            subject = "Demande de rendez-vous — Dossier [REFERENCE]",
            bodyTemplate = """Madame, Monsieur,

Je souhaiterais convenir d’un rendez-vous avec un conseiller ou un responsable concernant [MOTIF_PRECIS] (dossier n° [REFERENCE]).

Je suis joignable par e-mail à [EMAIL_EXPEDITEUR]${""} afin de fixer le créneau qui vous conviendra le mieux.

Dans l’attente de votre retour, je vous prie d’agréer, Madame, Monsieur, mes salutations distinguées.

[NOM_EXPEDITEUR]"""
        )
    )

    fun fillTemplate(template: LetterTemplate, data: LetterFillData): Pair<String, String> {
        val cleanAmount = data.amountText.trim()
            .replace("€", "")
            .trim()
            .let { if (it.isNotBlank()) "$it €" else "À préciser" }

        fun apply(raw: String): String = raw
            .replace("[NOM_EXPEDITEUR]", data.senderName.trim().ifBlank { "Prénom Nom" })
            .replace("[EMAIL_EXPEDITEUR]", data.senderEmail.trim().ifBlank { "votre.email@exemple.fr" })
            .replace("[ADRESSE_EXPEDITEUR]", data.senderAddress.trim().ifBlank { "Votre adresse postale" })
            .replace("[VILLE_EXPEDITEUR]", data.senderCity.trim().ifBlank { "Votre ville" })
            .replace("[TELEPHONE_EXPEDITEUR]", data.senderPhone.trim())
            .replace("[ORGANISME]", data.orgName.trim().ifBlank { "Nom de l’organisme" })
            .replace("[ADRESSE_ORGANISME]", data.orgAddress.trim())
            .replace("[NOM_CONTRAT]", data.contractName.trim().ifBlank { template.title })
            .replace("[REFERENCE]", data.referenceNumber.trim().ifBlank { "À compléter" })
            .replace("[MONTANT]", cleanAmount)
            .replace("[MOTIF_PRECIS]", data.reasonText.trim().ifBlank { "préciser le motif de votre demande" })
            .replace("[DATE_JOUR]", data.dateLabel.ifBlank { DateUtils.todayNumeric() })

        return apply(template.subject) to apply(template.bodyTemplate)
    }
}
