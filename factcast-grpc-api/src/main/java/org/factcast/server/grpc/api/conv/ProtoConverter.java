package org.factcast.server.grpc.api.conv;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.util.FCJson;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Fact;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_SubscriptionRequest;
import org.factcast.server.grpc.gen.FactStoreProto.MSG_UUID;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

//TODO add symetry tests
@RequiredArgsConstructor
public class ProtoConverter {

	public MSG_Notification toCatchupNotification() {
		return MSG_Notification.newBuilder().setType(MSG_Notification.Type.Catchup).build();
	}

	public MSG_Notification toCompleteNotification() {
		return MSG_Notification.newBuilder().setType(MSG_Notification.Type.Complete).build();

	}

	public MSG_Notification toNotification(Fact t) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification.Builder b = MSG_Notification.newBuilder()
				.setType(MSG_Notification.Type.Fact);
		b.setFact(toProto(t));
		b.setType(MSG_Notification.Type.Fact);
		return b.build();

	}

	public MSG_Notification toIdNotification(Fact t) {
		org.factcast.server.grpc.gen.FactStoreProto.MSG_Notification.Builder b = MSG_Notification.newBuilder()
				.setType(MSG_Notification.Type.Id);
		b.setId(toProto(t.id()));
		b.setType(MSG_Notification.Type.Id);
		return b.build();
	}

	public MSG_UUID toProto(@NonNull UUID t) {
		return MSG_UUID.newBuilder().setLsb(t.getLeastSignificantBits()).setMsb(t.getMostSignificantBits()).build();
	}

	@SneakyThrows
	public SubscriptionRequestTO fromProto(@NonNull MSG_SubscriptionRequest request) {
		return FCJson.reader().forType(SubscriptionRequestTO.class).readValue(request.getJson());
	}

	@SneakyThrows
	public MSG_SubscriptionRequest toProto(SubscriptionRequestTO request) {
		return MSG_SubscriptionRequest.newBuilder().setJson(FCJson.writer().writeValueAsString(request)).build();
	}

	public UUID fromProto(MSG_UUID request) {
		long lsb = request.getLsb();
		long msb = request.getMsb();

		return new UUID(msb, lsb);
	}

	public Fact fromProto(MSG_Fact protoFact) {
		return Fact.of(protoFact.getHeader(), protoFact.getPayload());
	}

	public MSG_Fact toProto(org.factcast.core.Fact factMark) {
		MSG_Fact.Builder proto = MSG_Fact.newBuilder();
		proto.setPresent(true);
		proto.setHeader(factMark.jsonHeader());
		proto.setPayload(factMark.jsonPayload());
		return proto.build();
	}

	public MSG_Fact toProto(Optional<Fact> optionalFact) {
		MSG_Fact.Builder proto = MSG_Fact.newBuilder();
		boolean present = optionalFact.isPresent();
		proto.setPresent(present);
		if (present) {
			Fact fact = optionalFact.get();
			proto.setHeader(fact.jsonHeader());
			proto.setPayload(fact.jsonPayload());
		}
		return proto.build();
	}

	

}
