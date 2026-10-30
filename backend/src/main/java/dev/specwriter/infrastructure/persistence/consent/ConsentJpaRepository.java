package dev.specwriter.infrastructure.persistence.consent;

import dev.specwriter.domain.consent.ConsentRecord;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.port.ConsentRepository;
import dev.specwriter.domain.port.IdGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Optional;

@ApplicationScoped
public class ConsentJpaRepository implements ConsentRepository {

    @Inject
    EntityManager em;

    @Inject
    IdGenerator ids;

    @Override
    @Transactional
    public void record(ConsentRecord record) {
        ConsentRecordEntity e = new ConsentRecordEntity();
        e.id = ids.next().toString();
        e.service = record.service().name();
        e.granted = record.granted();
        e.at = record.at();
        em.persist(e);
    }

    @Override
    @Transactional
    public Optional<ConsentRecord> latest(ConsentService service) {
        // rowid départage deux enregistrements à la même date : le plus récent inséré fait foi.
        return em.createNativeQuery("select * from consent_record where service = ?1 order by at desc, rowid desc",
                        ConsentRecordEntity.class)
                .setParameter(1, service.name())
                .setMaxResults(1)
                .getResultStream()
                .map(ConsentRecordEntity.class::cast)
                .findFirst()
                .map(e -> new ConsentRecord(ConsentService.valueOf(e.service), e.granted, e.at));
    }
}
