package fr.kawamind.infrastructure.persistence.settings;

import fr.kawamind.domain.port.ConfluenceSettingsRepository;
import fr.kawamind.domain.settings.ConfluenceSettings;
import fr.kawamind.domain.settings.ConfluenceTestResult;
import fr.kawamind.domain.settings.SecretPersistence;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Table {@code confluence_settings} (V27) : une seule ligne, jamais le jeton. */
@ApplicationScoped
public class ConfluenceSettingsSqliteRepository implements ConfluenceSettingsRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<ConfluenceSettings> find() {
        List<Object[]> rows = em.createNativeQuery("""
                select server_url, ca_certificate, insecure_http, server_version, last_test_at, last_test_result,
                       secret_persistence from confluence_settings where id = 1""").getResultList();
        return rows.stream().findFirst().map(r -> new ConfluenceSettings((String) r[0], (String) r[1], (String) r[3],
                r[4] == null || r[5] == null ? null
                        : new ConfluenceSettings.LastTest(Instant.parse((String) r[4]),
                                ConfluenceTestResult.valueOf((String) r[5])),
                SecretPersistence.valueOf((String) r[6]), ((Number) r[2]).intValue() == 1));
    }

    @Override
    @Transactional
    public void save(ConfluenceSettings s) {
        em.createNativeQuery("""
                insert into confluence_settings (id, server_url, ca_certificate, insecure_http, server_version,
                    last_test_at, last_test_result, secret_persistence) values (1, ?1, ?2, ?3, ?4, ?5, ?6, ?7)
                on conflict (id) do update set server_url = excluded.server_url,
                    ca_certificate = excluded.ca_certificate, insecure_http = excluded.insecure_http,
                    server_version = excluded.server_version, last_test_at = excluded.last_test_at,
                    last_test_result = excluded.last_test_result, secret_persistence = excluded.secret_persistence""")
                .setParameter(1, s.serverUrl()).setParameter(2, s.caCertificatePath())
                .setParameter(3, s.insecureHttp() ? 1 : 0).setParameter(4, s.serverVersion())
                .setParameter(5, s.lastTest() == null ? null : s.lastTest().at().toString())
                .setParameter(6, s.lastTest() == null ? null : s.lastTest().result().name())
                .setParameter(7, s.secretPersistence().name()).executeUpdate();
    }

    @Override
    @Transactional
    public void delete() {
        em.createNativeQuery("delete from confluence_settings").executeUpdate();
    }
}
