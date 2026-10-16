package com.budgettracker.application.port;

import java.util.List;

public interface AutoCategorizer {

    /**
     * Given a list of payee strings and the household's available category names,
     * returns a best-guess category name for each payee.
     * Returns an empty list if the service is unavailable (e.g. no API key configured).
     */
    List<Suggestion> suggest(List<String> payees, List<String> categoryNames);

    record Suggestion(String payee, String categoryName) {}
}
