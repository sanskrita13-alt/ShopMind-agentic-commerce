package com.shopmind.service.ai;

import com.shopmind.dto.AiDtos.ExtractedIntent;
import com.shopmind.dto.AiDtos.ProductData;
import com.shopmind.dto.AiDtos.ProductMatch;
import com.shopmind.dto.AiDtos.QuestionDecision;

import java.util.List;
import java.util.Map;

/**
 * Contract for AI services used by the conversation pipeline.
 * Allows mock and real implementations to be swapped via configuration.
 */
public interface AiService {

    /**
     * Extract structured intent from the full conversation history.
     *
     * @param conversationHistory ordered list of {role, content} maps
     * @return populated ExtractedIntent with confidence score and missing attributes
     */
    ExtractedIntent extractIntent(List<Map<String, String>> conversationHistory);

    /**
     * Decide whether to recommend products or ask a follow-up question.
     *
     * @param intent        current extracted intent
     * @param questionCount number of questions already asked in this session
     * @return decision with readyToRecommend flag and optional next question text
     */
    QuestionDecision decideNextQuestion(ExtractedIntent intent, int questionCount);

    /**
     * Score and rank the given products against the extracted intent.
     * Returns at most 3 ranked ProductMatch results sorted by matchScore descending.
     *
     * @param products pre-filtered candidate products
     * @param intent   the current session intent
     */
    List<ProductMatch> rankProducts(List<ProductData> products, ExtractedIntent intent);
}
