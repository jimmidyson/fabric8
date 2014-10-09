## Insight

Fabric8 is built for distributed deployments: distributed across container, VMs & datacentres.
Each container logs & gathers metrics which is great, but viewing those logs & metrics across a distributed
deployment isn't going to be easy. So Fabric8 provides a consolidated view on logs & metrics collected in your
fabric, making it easy to know exactly what's going on in your entire deployment.

### Logs & Metrics

Insight collects logs & metrics separately, allowing you to choose what you want to collect & where you want
to store your data. Out of the box, Fabric8 Insight collects a reasonable amount of data so you have to think
about how you're going to deploy Insight. Currently, Insight stores log data in Elasticsearch & metrics data
can be stored in either Elasticsearch, Cassandra (through RHQ Metrics), or InfluxDB (only available when
using Fabric8 with Docker). This can all be run on one node (see installation below), but in a production
deployment you are recommended to deploy a separate cluster of nodes to collect your data. This cluster can
all be managed through Fabric8 profiles. 

### Installation

Installing Insight is very simple: just as with anything else in Fabric8, it's all done by assigning profiles.
You can install logs & metrics separately so you can choose what pieces of Insight you actually install. When
using Insight Logs, you only have one choice for storage (Elasticsearch). With Insight Metrics you have 3 choices:
Elasticsearch, RHQ Metrics (Cassandra) & InfluxDB.

#### Installing Insight Logs

First thing to do is create a data node for Elasticsearch. Just assign the `insight-elasticsearch.datastore` profile
to an existing container or create a new one. If you're doing this all on one container you can simply do:

    container-add-profile root insight-elasticsearch.datastore

Or if you want to create a separate cluster of 3 Elasticsearch nodes you can simply run:

    container-create-child --profile insight-elasticsearch.datastore root ds 3

Elasticsearch will use the Fabric8 Zookeeper registry for discovery.

So now you have somewhere to store your data, you now need to tell your containers to start logging to
Elasticsearch. Just add the `insight-logs.elasticsearch` profile to any container that you want to send its logs
to Elasticsearch:

    container-add-profile root insight-logs.elasticsearch

Now that you have logs being collected in Elasticsearch, you need something to view & filter them. This is provided
by the `insight-console` profile:

    container-add-profile root insight-console

Hawtio will then have a new perspective available: Fabric8. This will have a tab called `Logs` which provides a
Kibana dashboard for you to search all logs across your fabric.

#### Installing Insight Metrics

##### Elasticsearch

After creating an Elasticsearch cluster as described above, then all you need to do is:

    container-add-profile root insight-metrics.elasticsearch

This will ensure that all metrics are sent to the Elasticsearch cluster that you created previously.
 
##### RHQ Metrics

RHQ Metrics uses Cassandra to store metrics data. To create a Cassandra container to store the data:

    container-create-child --profile containers-services-cassandra root cassandra
    
You can then add the `insight-metrics.rhq` profile to any container you want to log metrics to the
Cassandra database you just created:

    container-add-profile root insight-metrics.rhq

### The Insight Console

The Insight Console uses the Hawtio Kibana plugin. This provides a very flexible & powerful way to inspect your logs
& find what you’re looking for. The Log dashboard looks like this:

![Viewing Insight Logs](/images/insight-logs.png)

Each log entry can be expanded to view full details of the log event & stacktraces are formatted nicely, including
linking to source code for easy fault finding:

![Expanding Log Events](/images/insight-exception-stacktrace.png)
