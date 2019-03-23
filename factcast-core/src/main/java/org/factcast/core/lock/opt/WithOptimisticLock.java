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
package org.factcast.core.lock.opt;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.lock.Attempt;
import org.factcast.core.lock.AttemptAbortedException;
import org.factcast.core.lock.ExceptionAfterPublish;
import org.factcast.core.lock.IntermediatePublishResult;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Accessors(fluent = true, chain = true)
public class WithOptimisticLock {
    @NonNull
    final FactStore store;

    @NonNull
    final String ns;

    @NonNull
    final List<UUID> ids;

    @Setter
    private int retry = 10;

    @Setter
    private long interval = 0;

    private int count = 0;

    @NonNull
    public UUID attempt(@NonNull Attempt operation) throws AttemptAbortedException,
            OptimisticRetriesExceededException,
            ExceptionAfterPublish {
        while (++count <= retry) {

            // fetch current state
            StateToken token = store.stateFor(ns, ids);
            try {

                // execute the business logic
                // in case an AttemptAbortedException is thrown, just pass it
                // through
                IntermediatePublishResult r = operation.run();

                UUID lastFactId = lastFactId(r.factsToPublish());

                // try to publish
                if (store.publishIfUnchanged(token, r.factsToPublish())) {

                    // publishing worked
                    // now run the 'andThen' operation
                    try {
                        r.andThen().ifPresent(Runnable::run);
                    } catch (Throwable e) {
                        throw new ExceptionAfterPublish(lastFactId, e);
                    }

                    // and return the lastFactId for reference
                    return lastFactId;

                } else {
                    sleep();
                }
            } finally {
                store.invalidate(token);
            }
        }

        throw new OptimisticRetriesExceededException(retry);
    }

    @SneakyThrows
    private void sleep() {
        if (interval > 0)
            Thread.sleep(interval);
    }

    private UUID lastFactId(@NonNull List<Fact> factsToPublish) {

        if (factsToPublish.isEmpty())
            throw new IllegalArgumentException("Need to actually publish a Fact");

        return factsToPublish.get(factsToPublish.size() - 1).id();
    }

    @Getter
    public static final class OptimisticRetriesExceededException extends
            ConcurrentModificationException {

        private static final long serialVersionUID = 1L;

        private int retries;

        public OptimisticRetriesExceededException(int retry) {
            super("Exceeded the maximum number of retrys allowed (" + retry + ")");
            retries = retry;
        }

    }
}