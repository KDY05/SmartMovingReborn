package io.github.kdy05.smartmovingreborn.logic.crawl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CrawlLogicTest {
    @Test
    void crawlingStaysDownWhileStandingDoesNotFit() {
        // A crawling player stays down even where crouching would fit, like the original.
        assertTrue(CrawlLogic.mustCrawl(true, false, true, false, true));
        assertFalse(CrawlLogic.mustCrawl(true, true, true, false, true));
    }

    @Test
    void standingPlayerIsForcedOnlyWhenCrouchingDoesNotFitEither() {
        assertFalse(CrawlLogic.mustCrawl(false, false, true, false, true));
        assertTrue(CrawlLogic.mustCrawl(false, false, false, false, true));
    }

    @Test
    void flyingWithItsOwnSmallSizeIsNeverForced() {
        assertFalse(CrawlLogic.mustCrawl(true, false, false, true, true));
        assertTrue(CrawlLogic.mustCrawl(true, false, false, true, false));
    }

    @Test
    void holdingSneakOrGrabWithoutFreeClimbingContinuesCrawling() {
        assertTrue(CrawlLogic.inputContinueCrawl(false, false, true, true, false));
        assertFalse(CrawlLogic.inputContinueCrawl(false, false, false, true, true));
        assertTrue(CrawlLogic.inputContinueCrawl(false, false, false, false, true));
    }

    @Test
    void toggleModeOnlyLooksAtTheToggle() {
        assertTrue(CrawlLogic.inputContinueCrawl(true, true, false, true, false));
        assertFalse(CrawlLogic.inputContinueCrawl(true, false, true, false, true));
    }

    @Test
    void crawlingStartsWithGrabWhileSneakingOnTheGround() {
        assertTrue(CrawlLogic.wantCrawl(true, false, false, false, true, true, true));
        assertFalse(CrawlLogic.wantCrawl(true, false, false, false, true, true, false));
        assertFalse(CrawlLogic.wantCrawl(true, false, false, false, true, false, true));
        assertFalse(CrawlLogic.wantCrawl(true, false, false, false, false, true, true));
    }

    @Test
    void crawlingContinuesWithInputAndNeverWhileFlyingOrDisabled() {
        assertTrue(CrawlLogic.wantCrawl(true, true, false, true, false, false, false));
        assertFalse(CrawlLogic.wantCrawl(true, true, false, false, false, false, false));
        assertFalse(CrawlLogic.wantCrawl(true, true, true, true, false, false, false));
        assertFalse(CrawlLogic.wantCrawl(false, true, false, true, true, true, true));
    }
}
