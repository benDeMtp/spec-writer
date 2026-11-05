workspace extends ../model.dsl {

    name "Facturation"

    model {
        !impliedRelationships false

        archetypes {
            topic = container {
                tag topic
            }
        }

        !element suez {
            avoirsEmis = topic "avoirs-emis" "Avoirs émis par la facturation" "Kafka topic"
        }

        facturation = softwareSystem "Facturation" "Émission des factures et des avoirs" {
            api = container "API Facturation" "Expose la facturation" "Quarkus"
            avoirs = container "Service Avoirs" "Calcule et émet les avoirs" "Quarkus" {
                properties {
                    "ownership" "facturation"
                }
            }
            portail = container "Portail Client" "Consultation des factures par le client" "Angular"
        }

        !element comptabilite {
            ecritures = container "Ecritures" "Passe les écritures comptables" "Java"
            grandLivre = container "Grand Livre" "Tient le grand livre" "Java" {
                properties {
                    "ownership" "comptabilite"
                }
            }
        }

        client -> portail "Consulte ses factures" "HTTPS"
        portail -> api "Lit les factures" "HTTPS"
        api -> avoirs "Demande un avoir" "HTTPS"
        avoirs -> avoirsEmis "Publie les avoirs émis" "Kafka"
        ecritures -> avoirsEmis "Consomme les avoirs émis" "Kafka"
        ecritures -> grandLivre "Reporte les écritures" "JDBC"

        !include "../infra/prod.dsl"

        !element production {
            azure = deploymentNode "Azure" "Cloud Azure du projet" {
                aks = deploymentNode "AKS" "Kubernetes managé" {
                    containerInstance api
                    containerInstance avoirs
                }
            }
        }

        !element openshift {
            containerInstance ecritures
            containerInstance api
        }
    }
}
