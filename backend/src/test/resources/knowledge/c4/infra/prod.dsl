production = deploymentEnvironment "Production" {
    itaas = deploymentNode "ITaaS" "Hébergement interne partagé" {
        openshift = deploymentNode "OpenShift" "Kubernetes interne"
    }
}
