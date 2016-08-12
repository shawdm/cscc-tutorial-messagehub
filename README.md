# cscc-tutorial-messagehub

## Message Hub

* All cases each consumer has a unique client ID

## Setting up Topic

## Editing configuration

## Single Consumer

    ./start-producer.sh
    ./start-consumer-1.sh

Message Hub acts as buffer with the producer and consumer decoupled.  The consumer will process the data as quickly as it can, but wont be overloaded with input data.  The consumer can disconnect and it will get data from where it left off (last commit).


## Multiple Consumers, Different Consumer Group
* Set each consumer to have a unique client ID
* Set each consumer to have a unique consumer group name

    ./start-producer.sh
    ./start-consumer-1.sh
    ./start-consumer-2.sh

Message Hub working on publish-subscribe model.  Each consumer gets all the messages.  If one disconnects and reconnects it will carry on from where it left off.


## Multiple Consumers, Same Consumer Group
* Set each consumer to have a unique client ID
* Set each consumer to have the same consumer group name

    ./start-producer.sh
    ./start-consumer-1.sh
    ./start-consumer-2.sh

Message Hub delivers each message to just one instance in a given consumer group.  This can be used for load balancing.  Default behavior is round-robin, but can be defined through the API.

Must have at least as many partitions as there are consumer groups to load balance across all the consumer groups.  It is ok to have more partitions as a single consumer group can span multiple partitions.


## Single Consumer, New Consumer Group
* Set consumer-1 to have a new consumer group name
* Leave consumer-2 with its existing consumer group name
* Set auto.offset.reset Kafka property to earliest

    ./start-producer.sh
    ./start-consumer-1.sh
    ./start-consumer-1.sh

With doing real time analytics you normally have to do a combination of real time processing and reading from stored values. Message Hub can be used as a Kappa Architecture, where there is no traditional data store, only an append-only immutable log.  The idea is that you store the transactions, not the calculated values.

In message hub you can reset the position in the log that you start processing from.  The default is to process from the last committed message, but this can be reset to the beginning in the API.  The other alternative is to use a new consumer group name (commits are stored against a consumer group).  If there is a new consumer group, Message Hub uses the auto.offset.reset configuration property to work out where in the queue to start from (e.g. earliest).


http://milinda.pathirage.org/kappa-architecture.com/
