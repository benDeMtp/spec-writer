workspace extends ../model.dsl {

    model {
        !script groovy {
            workspace.name = "piégé"
        }
        scripte = softwareSystem "Scripté"
    }

    !script ../scripts/tags.groovy
}
