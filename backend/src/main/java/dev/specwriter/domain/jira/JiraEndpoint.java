package dev.specwriter.domain.jira;

/** Adresse, jeton et autorité de certification d'un serveur Jira. {@code toString} masque le jeton. */
public record JiraEndpoint(String serverUrl, String token, String caCertificatePath) {

    @Override
    public String toString() {
        return "JiraEndpoint[serverUrl=" + serverUrl + ", token=***, caCertificatePath=" + caCertificatePath + "]";
    }
}
