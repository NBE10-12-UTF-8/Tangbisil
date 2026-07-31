package com.back.domain.trend.keyword;

import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.AttributeFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class NounExtractor {

    // 일반명사(NNG)·고유명사(NNP)만 채택한다.
    // 의존명사(NNB, "것"/"수"/"때")나 대명사(NP)는 그 자체로 트렌드 키워드가 될 수 없어 제외한다.
    private static final Set<POS.Tag> NOUN_TAGS = EnumSet.of(POS.Tag.NNG, POS.Tag.NNP);

    public List<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> nouns = new ArrayList<>();

        try (KoreanTokenizer tokenizer = new KoreanTokenizer(
                AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY,
                null,                                  // 사용자 사전 없음
                KoreanTokenizer.DecompoundMode.NONE,    // 복합명사를 쪼개지 않고 통째로 유지
                false,                                  // 미등록 단어를 음절 단위로 강제 분해하지 않음
                true                                    // 문장부호 토큰 버림
        )) {
            tokenizer.setReader(new StringReader(text));

            CharTermAttribute termAttr = tokenizer.addAttribute(CharTermAttribute.class);
            PartOfSpeechAttribute posAttr = tokenizer.addAttribute(PartOfSpeechAttribute.class);

            tokenizer.reset();
            while (tokenizer.incrementToken()) {
                if (NOUN_TAGS.contains(posAttr.getRightPOS())) {
                    nouns.add(termAttr.toString());
                }
            }
            tokenizer.end();
        } catch (IOException e) {
            throw new UncheckedIOException("형태소 분석 실패", e);
        }

        return nouns;
    }
}
