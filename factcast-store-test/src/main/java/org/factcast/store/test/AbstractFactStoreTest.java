/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.store.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.MarkFact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.IdObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;

import lombok.Getter;
import lombok.SneakyThrows;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public abstract class AbstractFactStoreTest {

    static final FactSpec ANY = FactSpec.ns("default");

    protected FactCast uut;

    @BeforeEach
    void setUp() {
        uut = FactCast.from(createStoreToTest());
    }

    protected abstract FactStore createStoreToTest();

    @DirtiesContext
    @Test
    protected void testEmptyStore() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            FactObserver observer = mock(FactObserver.class);
            Subscription s = uut.subscribeToFacts(SubscriptionRequest.catchup(ANY).fromScratch(),
                    observer);
            s.awaitComplete();
            verify(observer).onCatchup();
            verify(observer).onComplete();
            verify(observer, never()).onError(any());
            verify(observer, never()).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testUniquenessConstraint() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                final UUID id = UUID.randomUUID();
                uut.publish(Fact.of("{\"id\":\"" + id
                        + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
                uut.publish(Fact.of("{\"id\":\"" + id
                        + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
                fail();
            });
        });
    }

    @DirtiesContext
    @Test
    protected void testEmptyStoreFollowNonMatching() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            TestFactObserver observer = testObserver();
            uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromScratch(), observer)
                    .awaitCatchup();
            verify(observer).onCatchup();
            verify(observer, never()).onComplete();
            verify(observer, never()).onError(any());
            verify(observer, never()).onNext(any());
            uut.publishWithMark(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"other\"}", "{}"));
            uut.publishWithMark(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"other\"}", "{}"));
            observer.await(2);
            // the mark facts only
            verify(observer, times(2)).onNext(any());
            assertEquals(MarkFact.MARK_TYPE, observer.values.get(0).type());
            assertEquals(MarkFact.MARK_TYPE, observer.values.get(1).type());
        });
    }

    private TestFactObserver testObserver() {
        return spy(new TestFactObserver());
    }

    private static class TestFactObserver implements FactObserver {

        private final List<Fact> values = new CopyOnWriteArrayList<>();

        @Override
        public void onNext(Fact element) {
            values.add(element);
        }

        @SneakyThrows
        public void await(int count) {
            while (true) {
                if (values.size() >= count) {
                    return;
                } else {
                    Thread.sleep(50);
                }
            }
        }
    }

    @DirtiesContext
    @Test
    protected void testEmptyStoreFollowMatching() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            TestFactObserver observer = testObserver();
            uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromScratch(), observer)
                    .awaitCatchup();
            verify(observer).onCatchup();
            verify(observer, never()).onComplete();
            verify(observer, never()).onError(any());
            verify(observer, never()).onNext(any());
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            observer.await(1);
        });
    }

    @DirtiesContext
    @Test
    protected void testEmptyStoreEphemeral() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            TestFactObserver observer = testObserver();
            uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromNowOn(), observer)
                    .awaitCatchup();
            // nothing recieved
            verify(observer).onCatchup();
            verify(observer, never()).onComplete();
            verify(observer, never()).onError(any());
            verify(observer, never()).onNext(any());
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            observer.await(1);
            verify(observer, times(1)).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testEmptyStoreEphemeralWithCancel() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            TestFactObserver observer = testObserver();
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            Subscription subscription = uut.subscribeToFacts(SubscriptionRequest.follow(ANY)
                    .fromNowOn(), observer)
                    .awaitCatchup();
            // nothing recieved
            verify(observer).onCatchup();
            verify(observer, never()).onComplete();
            verify(observer, never()).onError(any());
            verify(observer, never()).onNext(any());
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            observer.await(1);
            verify(observer, times(1)).onNext(any());
            subscription.close();
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            Thread.sleep(100);
            // additional event not received
            verify(observer, times(1)).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testEmptyStoreFollowWithCancel() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            TestFactObserver observer = testObserver();
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            Subscription subscription = uut.subscribeToFacts(SubscriptionRequest.follow(ANY)
                    .fromScratch(), observer)
                    .awaitCatchup();
            // nothing recieved
            verify(observer).onCatchup();
            verify(observer, never()).onComplete();
            verify(observer, never()).onError(any());
            verify(observer, times(3)).onNext(any());
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            observer.await(4);
            subscription.close();
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            Thread.sleep(100);
            // additional event not received
            verify(observer, times(4)).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testEmptyStoreCatchupMatching() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            FactObserver observer = mock(FactObserver.class);
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            uut.subscribeToFacts(SubscriptionRequest.catchup(ANY).fromScratch(), observer)
                    .awaitComplete();
            verify(observer).onCatchup();
            verify(observer).onComplete();
            verify(observer, never()).onError(any());
            verify(observer).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testEmptyStoreFollowMatchingDelayed() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            TestFactObserver observer = testObserver();
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromScratch(), observer)
                    .awaitCatchup();
            verify(observer).onCatchup();
            verify(observer, never()).onComplete();
            verify(observer, never()).onError(any());
            verify(observer).onNext(any());
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            observer.await(2);
        });
    }

    @DirtiesContext
    @Test
    protected void testEmptyStoreFollowNonMatchingDelayed() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            TestFactObserver observer = testObserver();
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\"default\",\"type\":\"t1\"}", "{}"));
            uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromScratch(), observer)
                    .awaitCatchup();
            verify(observer).onCatchup();
            verify(observer, never()).onComplete();
            verify(observer, never()).onError(any());
            verify(observer).onNext(any());
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\"other\",\"type\":\"t1\"}", "{}"));
            observer.await(1);
        });
    }

    @DirtiesContext
    @Test
    protected void testFetchById() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            UUID id = UUID.randomUUID();
            uut.publish(
                    Fact.of("{\"id\":\"" + UUID.randomUUID()
                            + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            Optional<Fact> f = uut.fetchById(id);
            assertFalse(f.isPresent());
            uut.publish(Fact.of("{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                    "{}"));
            f = uut.fetchById(id);
            assertTrue(f.isPresent());
            assertEquals(id, f.map(Fact::id).orElse(null));
        });
    }

    @DirtiesContext
    @Test
    protected void testAnySubscriptionsMatchesMark() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            FactObserver observer = mock(FactObserver.class);
            UUID mark = uut.publishWithMark(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\""
                    + UUID.randomUUID() + "\",\"type\":\"noone_knows\"}", "{}"));
            ArgumentCaptor<Fact> af = ArgumentCaptor.forClass(Fact.class);
            doNothing().when(observer).onNext(af.capture());
            uut.subscribeToFacts(SubscriptionRequest.catchup(ANY).fromScratch(), observer)
                    .awaitComplete();
            verify(observer).onNext(any());
            assertEquals(mark, af.getValue().id());
            verify(observer).onComplete();
            verify(observer).onCatchup();
            verifyNoMoreInteractions(observer);
        });
    }

    @DirtiesContext
    @Test
    protected void testRequiredMetaAttribute() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            FactObserver observer = mock(FactObserver.class);
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\"default\",\"type\":\"noone_knows\"}",
                    "{}"));
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                    "{}"));
            FactSpec REQ_FOO_BAR = FactSpec.ns("default").meta("foo", "bar");
            uut.subscribeToFacts(SubscriptionRequest.catchup(REQ_FOO_BAR).fromScratch(), observer)
                    .awaitComplete();
            verify(observer).onNext(any());
            verify(observer).onCatchup();
            verify(observer).onComplete();
            verifyNoMoreInteractions(observer);
        });
    }

    @DirtiesContext
    @Test
    protected void testScriptedWithPayloadFiltering() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            FactObserver observer = mock(FactObserver.class);
            uut.publish(Fact.of(
                    "{\"id\":\"" + UUID.randomUUID()
                            + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
                    "{}"));
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                    "{}"));
            FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript(
                    "function (h,e){ return (h.hit=='me')}");
            uut.subscribeToFacts(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
                    .awaitComplete();
            verify(observer).onNext(any());
            verify(observer).onCatchup();
            verify(observer).onComplete();
            verifyNoMoreInteractions(observer);
        });
    }

    @DirtiesContext
    @Test
    protected void testScriptedWithHeaderFiltering() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            FactObserver observer = mock(FactObserver.class);
            uut.publish(Fact.of(
                    "{\"id\":\"" + UUID.randomUUID()
                            + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
                    "{}"));
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                    "{}"));
            FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript(
                    "function (h){ return (h.hit=='me')}");
            uut.subscribeToFacts(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
                    .awaitComplete();
            verify(observer).onNext(any());
            verify(observer).onCatchup();
            verify(observer).onComplete();
            verifyNoMoreInteractions(observer);
        });
    }

    @DirtiesContext
    @Test
    protected void testScriptedFilteringMatchAll() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            FactObserver observer = mock(FactObserver.class);
            uut.publish(Fact.of(
                    "{\"id\":\"" + UUID.randomUUID()
                            + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
                    "{}"));
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                    "{}"));
            FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript(
                    "function (h){ return true }");
            uut.subscribeToFacts(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
                    .awaitComplete();
            verify(observer, times(2)).onNext(any());
            verify(observer).onCatchup();
            verify(observer).onComplete();
            verifyNoMoreInteractions(observer);
        });
    }

    @DirtiesContext
    @Test
    protected void testScriptedFilteringMatchNone() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            FactObserver observer = mock(FactObserver.class);
            uut.publish(Fact.of(
                    "{\"id\":\"" + UUID.randomUUID()
                            + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
                    "{}"));
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                    + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                    "{}"));
            FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript(
                    "function (h){ return false }");
            uut.subscribeToFacts(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
                    .awaitComplete();
            verify(observer).onCatchup();
            verify(observer).onComplete();
            verifyNoMoreInteractions(observer);
        });
    }

    @DirtiesContext
    @Test
    protected void testIncludeMarks() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            final UUID id = UUID.randomUUID();
            uut.publishWithMark(Fact.of("{\"id\":\"" + id
                    + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            FactObserver observer = mock(FactObserver.class);
            uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default")).fromScratch(),
                    observer)
                    .awaitComplete();
            verify(observer, times(2)).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testSkipMarks() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            final UUID id = UUID.randomUUID();
            uut.publishWithMark(Fact.of("{\"id\":\"" + id
                    + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
            FactObserver observer = mock(FactObserver.class);
            uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default"))
                    .skipMarks()
                    .fromScratch(),
                    observer).awaitComplete();
            verify(observer, times(1)).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testMatchBySingleAggId() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            final UUID id = UUID.randomUUID();
            final UUID aggId1 = UUID.randomUUID();
            uut.publishWithMark(Fact.of(
                    "{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                            + aggId1 + "\"]}",
                    "{}"));
            FactObserver observer = mock(FactObserver.class);
            uut.subscribeToFacts(
                    SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId1))
                            .skipMarks()
                            .fromScratch(),
                    observer).awaitComplete();
            verify(observer, times(1)).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testMatchByOneOfAggId() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            final UUID id = UUID.randomUUID();
            final UUID aggId1 = UUID.randomUUID();
            final UUID aggId2 = UUID.randomUUID();
            uut.publishWithMark(Fact.of("{\"id\":\"" + id
                    + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                    + aggId1 + "\",\"" + aggId2 + "\"]}", "{}"));
            FactObserver observer = mock(FactObserver.class);
            uut.subscribeToFacts(
                    SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId1))
                            .skipMarks()
                            .fromScratch(),
                    observer).awaitComplete();
            verify(observer, times(1)).onNext(any());
            observer = mock(FactObserver.class);
            uut.subscribeToFacts(
                    SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId2))
                            .skipMarks()
                            .fromScratch(),
                    observer).awaitComplete();
            verify(observer, times(1)).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testMatchBySecondAggId() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            final UUID id = UUID.randomUUID();
            final UUID aggId1 = UUID.randomUUID();
            final UUID aggId2 = UUID.randomUUID();
            uut.publishWithMark(Fact.of("{\"id\":\"" + id
                    + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                    + aggId1 + "\",\"" + aggId2 + "\"]}", "{}"));
            FactObserver observer = mock(FactObserver.class);
            uut.subscribeToFacts(
                    SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId2))
                            .skipMarks()
                            .fromScratch(),
                    observer).awaitComplete();
            verify(observer, times(1)).onNext(any());
        });
    }

    @DirtiesContext
    @Test
    protected void testDelayed() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            final UUID id = UUID.randomUUID();
            TestFactObserver obs = new TestFactObserver();
            try (Subscription s = uut.subscribeToFacts(
                    SubscriptionRequest.follow(500, FactSpec.ns("default").aggId(id))
                            .skipMarks()
                            .fromScratch(), obs)) {
                uut.publishWithMark(Fact.of(
                        "{\"id\":\"" + id
                                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id
                                + "\"]}",
                        "{}"));
                // will take some time on pgstore
                obs.await(1);
            }
        });
    }

    @DirtiesContext
    @Test
    protected void testSerialOf() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            final UUID id = UUID.randomUUID();
            assertFalse(uut.serialOf(id).isPresent());
            UUID mark1 = uut.publishWithMark(Fact.of(
                    "{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                            + id + "\"]}",
                    "{}"));
            assertTrue(uut.serialOf(mark1).isPresent());
            assertTrue(uut.serialOf(id).isPresent());
            long serMark = uut.serialOf(mark1).getAsLong();
            long serFact = uut.serialOf(id).getAsLong();
            assertTrue(serFact < serMark);
        });
    }

    @DirtiesContext
    @Test
    protected void testSerialHeader() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            UUID id = UUID.randomUUID();
            uut.publish(Fact.of(
                    "{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                            + id + "\"]}",
                    "{}"));
            UUID id2 = UUID.randomUUID();
            uut.publish(Fact.of("{\"id\":\"" + id2
                    + "\",\"type\":\"someType\",\"meta\":{\"foo\":\"bar\"},\"ns\":\"default\",\"aggIds\":[\""
                    + id2
                    + "\"]}", "{}"));
            OptionalLong serialOf = uut.serialOf(id);
            assertTrue(serialOf.isPresent());
            Fact f = uut.fetchById(id).get();
            Fact fact2 = uut.fetchById(id2).get();
            assertEquals(serialOf.getAsLong(), f.serial());
            assertTrue(f.before(fact2));
        });
    }

    @DirtiesContext
    @Test
    protected void testUniqueIdentConstraintInLog() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            String ident = UUID.randomUUID().toString();
            UUID id = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Fact f1 = Fact.of("{\"id\":\"" + id
                    + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id
                    + "\"],\"meta\":{\"unique_identifier\":\"" + ident + "\"}}", "{}");
            Fact f2 = Fact.of("{\"id\":\"" + id2
                    + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id2
                    + "\"],\"meta\":{\"unique_identifier\":\"" + ident + "\"}}", "{}");
            uut.publish(f1);
            // needs to fail due to uniqueIdentitfier not being unique
            try {
                uut.publish(f2);
                fail("Expected IllegalArgumentException due to unique_identifier being used a sencond time");
            } catch (IllegalArgumentException e) {
                // make sure, f1 was stored before
                assertTrue(uut.fetchById(id).isPresent());
            }
        });
    }

    @DirtiesContext
    @Test
    protected void testUniqueIdentConstraintInBatch() {
        Assertions.assertTimeout(Duration.ofMillis(30000), () -> {
            String ident = UUID.randomUUID().toString();
            UUID id = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Fact f1 = Fact.of("{\"id\":\"" + id
                    + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id
                    + "\"],\"meta\":{\"unique_identifier\":\"" + ident + "\"}}", "{}");
            Fact f2 = Fact.of("{\"id\":\"" + id2
                    + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id2
                    + "\"],\"meta\":{\"unique_identifier\":\"" + ident + "\"}}", "{}");
            // needs to fail due to uniqueIdentitfier not being unique
            try {
                uut.publish(Arrays.asList(f1, f2));
                fail("Expected IllegalArgumentException due to unique_identifier being used twice in a batch");
            } catch (IllegalArgumentException e) {
                // make sure, f1 was not stored either
                assertFalse(uut.fetchById(id).isPresent());
            }
        });
    }

    @Test
    protected void testChecksMandatoryNamespaceOnPublish() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\"}",
                    "{}"));
        });
    }

    @Test
    protected void testChecksMandatoryIdOnPublish() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            uut.publish(Fact.of("{\"ns\":\"default\",\"type\":\"someType\"}", "{}"));
        });
    }

    @Test
    protected void testEnumerateNameSpaces() {
        // no namespaces
        assertEquals(0, uut.enumerateNamespaces().size());
        uut.publish(Fact.builder().ns("ns1").build("{}"));
        uut.publish(Fact.builder().ns("ns2").build("{}"));
        Set<String> ns = uut.enumerateNamespaces();
        assertEquals(2, ns.size());
        assertTrue(ns.contains("ns1"));
        assertTrue(ns.contains("ns2"));
    }

    @Test
    protected void testEnumerateTypesNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.enumerateTypes(null);
        });
    }

    @Test
    protected void testEnumerateTypes() {
        uut.publish(Fact.builder().ns("ns1").type("t1").build("{}"));
        uut.publish(Fact.builder().ns("ns2").type("t2").build("{}"));
        assertEquals(1, uut.enumerateTypes("ns1").size());
        assertTrue(uut.enumerateTypes("ns1").contains("t1"));
        uut.publish(Fact.builder().ns("ns1").type("t1").build("{}"));
        uut.publish(Fact.builder().ns("ns1").type("t1").build("{}"));
        uut.publish(Fact.builder().ns("ns1").type("t2").build("{}"));
        uut.publish(Fact.builder().ns("ns1").type("t3").build("{}"));
        uut.publish(Fact.builder().ns("ns1").type("t2").build("{}"));
        assertEquals(3, uut.enumerateTypes("ns1").size());
        assertTrue(uut.enumerateTypes("ns1").contains("t1"));
        assertTrue(uut.enumerateTypes("ns1").contains("t2"));
        assertTrue(uut.enumerateTypes("ns1").contains("t3"));
    }

    @Test
    protected void testFollow() throws Exception {
        uut.publish(newFollowTestFact());
        AtomicReference<CountDownLatch> l = new AtomicReference<>(new CountDownLatch(1));
        SubscriptionRequest request = SubscriptionRequest.follow(FactSpec.ns("followtest"))
                .fromScratch();
        FactObserver observer = element -> l.get().countDown();
        uut.subscribeToFacts(request, observer);
        // make sure, you got the first one
        assertTrue(l.get().await(1500, TimeUnit.MILLISECONDS));

        l.set(new CountDownLatch(3));
        uut.publish(newFollowTestFact());
        uut.publish(newFollowTestFact());
        // needs to fail
        assertFalse(l.get().await(1500, TimeUnit.MILLISECONDS));
        assertEquals(1, l.get().getCount());

        uut.publish(newFollowTestFact());

        assertTrue(l.get().await(10, TimeUnit.SECONDS),
                "failed to see all the facts published within 10 seconds.");
    }

    private Fact newFollowTestFact() {
        return Fact.builder().ns("followtest").id(UUID.randomUUID()).build("{}");
    }

    @Test
    public void testFetchByIdNullParameter() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            createStoreToTest().fetchById(null);
        });
    }

    @Test
    public void testPublishNullParameter() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            createStoreToTest().publish(null);
        });
    }

    @Test
    public void testSubscribeStartingAfter() throws Exception {
        for (int i = 0; i < 10; i++) {
            uut.publish(Fact.builder().id(new UUID(0L, i)).ns("ns1").type("t1").build("{}"));
        }

        ToListObserver toListObserver = new ToListObserver();

        SubscriptionRequest request = SubscriptionRequest.catchup(FactSpec.ns("ns1")).from(new UUID(
                0L, 7L));
        Subscription s = uut.subscribeToFacts(request, toListObserver);
        s.awaitComplete();

        assertEquals(2, toListObserver.list().size());

    }

    static class ToListObserver implements FactObserver {
        @Getter
        private List<Fact> list = new LinkedList<>();

        @Override
        public void onNext(Fact element) {
            list.add(element);
        }

    }

    @Test
    public void testSubscribeToIdsParameterContract() throws Exception {
        IdObserver observer = mock(IdObserver.class);
        assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, observer);
        });
        assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(mock(SubscriptionRequestTO.class), null);
        });
    }

    @Test
    public void testSubscribeToFactsParameterContract() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, observer);
        });
        assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(mock(SubscriptionRequestTO.class), null);
        });
    }
}
