package fr.kawamind.infrastructure.web;

import fr.kawamind.application.interview.InterviewRevision;
import fr.kawamind.application.interview.RunInterview;
import fr.kawamind.domain.interview.Interview;
import fr.kawamind.domain.interview.Walkthrough;
import fr.kawamind.domain.port.InterviewRepository;
import fr.kawamind.domain.port.SessionRepository;
import fr.kawamind.domain.session.Session;
import fr.kawamind.infrastructure.web.InterviewResource.AnchorView;
import fr.kawamind.infrastructure.web.InterviewResource.CursorView;
import fr.kawamind.infrastructure.web.InterviewResource.InterviewDetail;
import fr.kawamind.infrastructure.web.InterviewResource.InterviewSummary;
import fr.kawamind.infrastructure.web.InterviewResource.ItemView;
import fr.kawamind.infrastructure.web.InterviewResource.PointView;
import fr.kawamind.infrastructure.web.InterviewResource.SpecName;
import fr.kawamind.infrastructure.web.InterviewResource.SpecView;
import fr.kawamind.infrastructure.web.InterviewResource.StopView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/** Vues des séances (021, contracts/rest-api.md), partagées par les ressources. */
@ApplicationScoped
public class InterviewViews {

    @Inject
    InterviewRepository interviews;

    @Inject
    SessionRepository sessions;

    @Inject
    InterviewRevision revision;

    InterviewDetail detail(RunInterview.View v) {
        Interview i = v.interview();
        List<SpecView> specs = v.specs().stream().map(s -> {
            String unavailable = revision.unavailable(i, s.spec().sessionId(), v.points(), s.spec().appliedVersion())
                    .orElse(null);
            return new SpecView(s.spec().sessionId(), s.session().name(), s.spec().rank(), s.spec().version(),
                    s.latestVersion(), s.spec().excluded().stream().sorted().toList(), s.spec().appliedVersion(),
                    unavailable == null, unavailable);
        }).toList();
        List<StopView> stops = v.walkthrough().stops().stream().map(InterviewViews::stop).toList();
        CursorView cursor = i.cursor() == null ? null
                : new CursorView(i.cursor().sessionId(), AnchorView.of(i.cursor().anchor()));
        return new InterviewDetail(i.id(), i.state().name(), i.participants(), i.createdAt(), i.startedAt(),
                i.finishedAt(), cursor, specs, stops, v.walkthrough().counts());
    }

    static StopView stop(Walkthrough.Stop s) {
        return new StopView(s.sessionId(), AnchorView.of(s.anchor()), s.kind().name(), s.label(), s.text(),
                s.items().stream().map(item -> new ItemView(item.origin().name(), item.code(), item.statement(),
                        item.point() == null ? null : PointView.of(item.point()), item.pending())).toList());
    }

    InterviewSummary summary(Interview i) {
        List<SpecName> names = interviews.specs(i.id()).stream()
                .map(s -> new SpecName(s.sessionId(), sessions.find(s.sessionId()).map(Session::name).orElse("")))
                .toList();
        return new InterviewSummary(i.id(), i.state().name(), i.participants(), i.createdAt(), i.startedAt(),
                i.finishedAt(), names, interviews.points(i.id()).size());
    }
}
