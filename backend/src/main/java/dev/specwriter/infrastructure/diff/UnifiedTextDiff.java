package dev.specwriter.infrastructure.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import dev.specwriter.domain.port.TextDiff;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.List;

/** Diff unifié ligne à ligne (java-diff-utils), 3 lignes de contexte. */
@ApplicationScoped
public class UnifiedTextDiff implements TextDiff {

    private static final int CONTEXT = 3;

    @Override
    public String unified(String before, String after, String beforeLabel, String afterLabel) {
        List<String> original = lines(before);
        List<String> revised = lines(after);
        List<String> out = UnifiedDiffUtils.generateUnifiedDiff(beforeLabel, afterLabel, original,
                DiffUtils.diff(original, revised), CONTEXT);
        return out.isEmpty() ? "" : String.join("\n", out) + "\n";
    }

    private static List<String> lines(String text) {
        return text == null || text.isEmpty() ? List.of() : Arrays.asList(text.split("\n", -1));
    }
}
