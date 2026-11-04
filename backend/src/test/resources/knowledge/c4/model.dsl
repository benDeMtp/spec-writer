workspace {

    model {
        client = person "Client" "Client facturé"
        comptabilite = softwareSystem "Comptabilite" "Grand livre et écritures comptables"
        suez = softwareSystem "Suez" "Bus d'événements Apache Kafka partagé" {
            tags "infra"
        }
    }
}
