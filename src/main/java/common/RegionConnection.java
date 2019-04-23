package common;

import com.google.common.graph.MutableValueGraph;
import nlp.NLPTools;

import java.util.*;
import java.util.stream.Collectors;

public class RegionConnection {
    private Set<String> intersection;
    private Set<String> sourceDiff;
    private Set<String> targetDiff;
    private List<String> sourceTokens;
    private List<String> targetTokens;

    public RegionConnection(MutableValueGraph<RectangleTokenAtRank, Double> rectangleTokens, MutableValueGraph<RectangleTokenAtRank, Double> otherRectangleTokens) {
//        fixTokenOrderRecursive(rectangleTokens.nodes(), null);
//        fixTokenOrderRecursive(otherRectangleTokens.nodes(), null);
//
        sourceTokens = rectangleTokens.nodes().stream().filter(p -> p.isRetain()).map(p -> p.getToken()).collect(Collectors.toList());//fixTokenOrder(rectangleTokens.nodes());
        targetTokens = otherRectangleTokens.nodes().stream().filter(p -> p.isRetain()).map(p -> p.getToken()).collect(Collectors.toList());//fixTokenOrder(otherRectangleTokens.nodes());

//        Set<String> tokens = rectangleTokens.nodes().stream()
//                .filter(p -> p.isRetain())
//                .map(p -> p.getToken())
//                .collect(Collectors.toSet());
//
//        Set<String> otherTokens = otherRectangleTokens.nodes().stream()
//                .filter(p -> p.isRetain())
//                .map(p -> p.getToken())
//                .collect(Collectors.toSet());

        intersection = new HashSet<>(sourceTokens);
        intersection.retainAll(targetTokens);

        sourceDiff = new HashSet<>(sourceTokens);
        sourceDiff.removeAll(targetTokens);

        targetDiff = new HashSet<>(targetTokens);
        targetDiff.removeAll(sourceTokens);
    }

    private List<String> fixTokenOrder(Set<RectangleTokenAtRank> rectangleTokens) {
        List<RectangleTokenAtRank> tokenList = rectangleTokens.stream()
                .filter(p -> p.isRetain())
                .collect(Collectors.toList());

        //reset all next pointers to null in case these rectangle tokens have been used previously
        tokenList.stream().forEach(p -> p.setNext(null));

        //the purpose of this loop is to wire up all of the next pointers on each token based on all previous pointers
        for (int i = 0; i < tokenList.size(); i++) {
            RectangleTokenAtRank token = tokenList.get(i);
            token.setEmitted(false);
//            if (token.getPrev() != null && i > 0) { //fallback test to see if the previous token is actually the right one... otherwise a more complex search is needed
//                RectangleTokenAtRank prevToken = tokenList.get(i - 1);
//                if (token.getPrev().equals(prevToken)) {
//                    prevToken.setNext(token);
//                    continue;
//                }
//            }
            for (int j = 0; j < tokenList.size(); j++) {
                RectangleTokenAtRank otherToken = tokenList.get(j);
                if (token != otherToken) {
                    if (token.getPrev() == null) {
                        if (otherToken.getPrev() != null && NLPTools.similarity(otherToken.getPrev().getToken(), token.getToken()) > 0.75) {
                            otherToken.setPrev(token);
                            token.setNext(otherToken);
                            break;
                        }
                    } else if (NLPTools.similarity(token.getPrev().getToken(), otherToken.getToken()) > 0.75) {
                        if (otherToken.getNext() != null) {
                            RectangleTokenAtRank next = otherToken.getNext();
                            if (next != token) {
                                //The other token's next pointer is pointing at a different token than the current token,
                                //and yet the current token's previous pointer is pointing at the other token.
                                //This implies there are two different tokens that point at other token as being previous to itself.
                                //We will realign pointers to insert the current token between the other token and the
                                //other token's "next" token.
                                next.setPrev(token);
                                token.setNext(next);
                                //At this point there are now two tokens that point (via their next pointers) at the other token's "next" token.
                                //This ambiguity will be resolved in the next step.
                            }
                        }
                        //Ensure that the current token's previous pointer is indeed pointing at the other token.  Note that this relationship is purely
                        //inferred via string similarity.  Further, ensure that the other token's next pointer is pointing at the current token.
                        token.setPrev(otherToken);
                        otherToken.setNext(token);
                        break;
                    }
                }
            }
        }

        List<String> tokens = new ArrayList<>();
        for (RectangleTokenAtRank token : tokenList) {
            if (token.getPrev() == null || !token.getPrev().isRetain()) { //found start of string
                tokens.add(token.getToken());
                token.setEmitted(true);
                RectangleTokenAtRank next = token.getNext();
                while (next != null) {
                    if (!next.isEmitted()) {
                        tokens.add(next.getToken());
                        next.setEmitted(true);
                        next = next.getNext();
                    } else {
                        break;
                    }
                }
            }
        }

        //if any non-emitted tokens are left over then emit those too
        for (RectangleTokenAtRank token : tokenList) {
            if (!token.isEmitted()) {
                tokens.add(token.getToken());
                token.setEmitted(true);
            }
        }

        return tokens;
    }

    private boolean checkAncestry(RectangleTokenAtRank token, RectangleTokenAtRank otherToken) {
        RectangleTokenAtRank prev = token.getPrev();
        while (prev != null) {
            if (prev.getToken().equals(otherToken.getToken())) {
                return true;
            }
            prev = prev.getPrev();
        }
        return false;
    }

//    private RectangleTokenAtRank fixTokenOrderRecursive(Set<RectangleTokenAtRank> rectangleTokens, RectangleTokenAtRank token) {
//        if (token == null) {
//            int numTokens = rectangleTokens.size();
//            RectangleTokenAtRank[] rectangleTokenArray = rectangleTokens.toArray(new RectangleTokenAtRank[numTokens]);
//            token = rectangleTokenArray[numTokens - 1];
//        }
//
//        for (RectangleTokenAtRank rectangleToken : rectangleTokens) {
//            if (token != rectangleToken && token.getToken().equals(rectangleToken.getToken())) {
//                token.setPrev(fixTokenOrderRecursive(rectangleTokens, rectangleToken));
//            }
//        }
//
//        if (token.isRetain()) {
//            return token;
//        } else {
//            return null;
//        }
//    }

    public Set<String> getIntersection() {
        return intersection;
    }

    public Set<String> getSourceDiff() {
        return sourceDiff;
    }

    public Set<String> getTargetDiff() {
        return targetDiff;
    }

    public List<String> getSourceTokens() {
        return sourceTokens;
    }

    public List<String> getTargetTokens() {
        return targetTokens;
    }
}
