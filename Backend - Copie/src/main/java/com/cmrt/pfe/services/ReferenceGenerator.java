package com.cmrt.pfe.services;

import com.cmrt.pfe.repositories.EngineeringChangeRepository;
import com.cmrt.pfe.repositories.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Year;

/**
 * Produces the human-readable keys people quote in meetings: {@code BLK-2026-014},
 * {@code ECR-2026-007}. Counting existing records keeps the sequence readable without a
 * separate counter collection; the synchronized block serialises concurrent creations
 * within the instance.
 */
@Service
@RequiredArgsConstructor
public class ReferenceGenerator {

    private final IssueRepository issueRepository;
    private final EngineeringChangeRepository engineeringChangeRepository;

    public synchronized String nextIssueReference() {
        return build("BLK", issueRepository.count());
    }

    public synchronized String nextChangeReference() {
        return build("ECR", engineeringChangeRepository.count());
    }

    private String build(String prefix, long existing) {
        return String.format("%s-%d-%03d", prefix, Year.now().getValue(), existing + 1);
    }
}
