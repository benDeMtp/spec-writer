workspace {

    name "Contrats"

    # Modèle de la feature 013 (périmètre des modules touchés), à rapprocher de l'arbre demo-rv :
    # facturation désigne son module par la propriété spec-writer.module, notification par son nom,
    # et Archives n'a pas de module.
    model {
        !impliedRelationships false

        contrats = softwareSystem "Contrats" "Gestion des contrats d'abonnement" {
            facturation = container "Facturation API" "Facture les contrats actifs" "Quarkus" {
                properties {
                    "spec-writer.module" "modules/facturation"
                }
            }
            notification = container "Notification" "Envoie les courriels aux clients" "Quarkus"
            archives = container "Archives" "Conserve les factures émises" "Stockage objet"
        }

        facturation -> notification "envoie les relances" "REST"
        facturation -> archives "archive les factures" "SFTP"
    }
}
