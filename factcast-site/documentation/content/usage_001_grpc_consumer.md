+++
draft = false
title = "GRPC Consumer"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "usage"
identifier = "grpc_consumer"
weight = 20

+++

## GRPC Comsumer

As mentioned [before]({{%relref "intro_02_design.md#read-subscribe"%}}), there are three main Use-Cases for subscribing to a Fact-Stream:

* Validation of Changes against a sctrictly consistent Model (Catchup)
* Creating and maintaining a Read-Model (Follow)
* Managing volatile cached data (Ephemeral)

Here is some example code assuming you use the Spring GRPC Client:

## Example Code: Catchup



```java
@Component
class CustomerRepository{
 @Autowired
 FactCast factCast;

 // oversimplified code !
 public Customer getCustomer(UUID customerId){
   // match all Facts currently published about that customer
   SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("myapp").aggId(customerId)).fromScratch();
   
   Customer customer = new Customer(id);
   // stream all these Facts to the customer object's handle method, and wait until the stream ends.
   factCast.subscribeToFacts(req, customer::handle ).awaitComplete();

   // the customer object should now be in its latest state, and ready for command validation
   return customer;
 }

}

class Customer {
  Money balance = new Money(0); // starting with no money.
  //...
  public void handle(Fact f){
    // apply Fact, so that the customer earns and spend some money...
  }
}
```


## Example Code: Follow

```java
@Component
class QueryOptimizedView {
 @Autowired
 FactCast factCast;

 @PostConstruct
 public void init(){

   UUID lastFactProcessed = persistentModel.getLastFactProcessed();

   // subscribe to all customer related changes.
   SubscriptionRequest req = SubscriptionRequest.follow(FactSpec.ns("myapp")
      .type("CustomerCreated")
      .type("CustomerDeleted")  
      .type("Purchase")  
      .type("CustomerDeposition")    
    ).from(lastFactProcessed);
   factCast.subscribeToFacts(req, this::handle );
 }

 @Transactional
 public void handle(Fact f){
    // apply Fact, to the persistent Model
    // ...
    persistentModel.setLastFactProcessed(f.id());
 }

```



## Example Code: Ephemeral

```java
@Component
class CustomerCache {
 @Autowired
 FactCast factCast;

 Map<UUID,Customer> customerCache = new HashMap<>();

 @PostConstruct
 public void init(){
   // subscribe to all customer related changes.
   SubscriptionRequest req = SubscriptionRequest.follow(FactSpec.ns("myapp")
      .type("CustomerCreated")
      .type("CustomerDeleted")  
      .type("Purchase")  
      .type("CustomerDeposition")    
    ).fromNowOn();
   factCast.subscribeToFacts(req, this::handle );
 }

 @Transactional
 public void handle(Fact f){
    // if anything has changed, invalidate the cached value.
    // ...
    UUID aggregateId = f.aggId();
    if (aggregateId!=null)
      customerCache.remove(aggregateId);
 }

```