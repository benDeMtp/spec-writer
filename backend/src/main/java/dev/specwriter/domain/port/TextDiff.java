package dev.specwriter.domain.port;

/** Différences ligne à ligne entre deux textes, au format diff unifié. */
public interface TextDiff {

    String unified(String before, String after, String beforeLabel, String afterLabel);
}
