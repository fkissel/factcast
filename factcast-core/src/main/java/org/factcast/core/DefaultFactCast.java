package org.factcast.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactObserver;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.GenericObserver;
import org.factcast.core.subscription.IdObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultFactCast implements FactCast {

	@NonNull
	private final FactStore store;

	@Override
	public CompletableFuture<Subscription> subscribeToFacts(@NonNull SubscriptionRequest req,
			@NonNull FactObserver observer) {
		return store.subscribe(SubscriptionRequestTO.forFacts(req),
				new ObserverBridge<Fact>(observer, Function.identity()));
	}

	@Override
	public CompletableFuture<Subscription> subscribeToIds(@NonNull SubscriptionRequest req,
			@NonNull IdObserver observer) {
		return store.subscribe(SubscriptionRequestTO.forIds(req), new ObserverBridge<UUID>(observer, f -> f.id()));
	}

	@Override
	public Optional<Fact> fetchById(@NonNull UUID id) {
		return store.fetchById(id);
	}

	@Override
	public void publish(@NonNull List<Fact> factsToPublish) {
		store.publish(factsToPublish);
	}

	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static class ObserverBridge<T> implements FactStoreObserver {

		private final GenericObserver<T> delegate;
		private final Function<Fact, T> project;

		@Override
		public void onNext(Fact f) {
			delegate.onNext(project.apply(f));
		}

		@Override
		public void onCatchup() {
			delegate.onCatchup();
		}

		@Override
		public void onError(Throwable e) {
			delegate.onError(e);
		}

		@Override
		public void onComplete() {
			delegate.onComplete();
		}
	}
}
