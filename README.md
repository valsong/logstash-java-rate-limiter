# logstash rate limit Filter plugin

A simple Java Filter plugin can use Guava RateLimiter to implement rate limiting.   
You can easily change the rate by modifying a text file,   
without the need to restart Logstash. The rate change will take effect within one second.

```
[2024-02-01T16:44:41,515][WARN ][org.logstash.plugins.filters.RateLimitFilter][Converge PipelineAction::Create<main>] ### Rate limiter enabled:[true]! ratePath:[/usr/share/logstash/rate.txt].
[2024-02-01T16:44:41,519][WARN ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-0] # Rate changed, set new RateLimiter! lastRate:[0.0] rate:[5000.0] ratePath:[/usr/share/logstash/rate.txt].
[2024-02-01T16:44:41,520][WARN ][org.logstash.plugins.filters.RateLimitFilter][Converge PipelineAction::Create<main>] ### Record event count to file enabled:[true]! countPath:[/usr/share/logstash/count.txt].
[2024-02-01T16:44:50,536][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-1] Event count:[36500] rate:[5000.0].
[2024-02-01T16:45:00,561][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-1] Event count:[87000] rate:[5000.0].
[2024-02-01T16:45:10,587][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-1] Event count:[137000] rate:[5000.0].
[2024-02-01T16:45:11,587][WARN ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-0] # Rate changed, set new RateLimiter! lastRate:[5000.0] rate:[6000.0] ratePath:[/usr/share/logstash/rate.txt].
[2024-02-01T16:45:20,591][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-0] Event count:[204000] rate:[6000.0].
[2024-02-01T16:45:30,595][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-0] Event count:[264000] rate:[6000.0].
[2024-02-01T16:45:40,638][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-0] Event count:[324000] rate:[6000.0].
[2024-02-01T16:45:50,647][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-0] Event count:[384000] rate:[6000.0].
[2024-02-01T16:46:00,649][WARN ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-1] # Rate changed, set new RateLimiter! lastRate:[6000.0] rate:[3000.0] ratePath:[/usr/share/logstash/rate.txt].
[2024-02-01T16:46:00,651][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-0] Event count:[444000] rate:[3000.0].
[2024-02-01T16:46:10,655][INFO ][org.logstash.plugins.filters.RateLimitFilter][rate-limit-0] Event count:[482000] rate:[3000.0].
```

# How to compile

- Please copy the logstash-core.jar file to the bin directory.

```
logstash-java-rate-limiter
 |_ bin
    |_  install-logstash-core.sh 
    |_  logstash-core.jar
```

- Navigate to the bin directory and proceed with installing logstash-core.jar to the local repository.

```shell
cd bin
sh ./install-logstash-core.sh
```

- Use JDK 8 to compile logstash-java-rate-limiter

```
mvn clean install -DskipTests
```

- Copy `logstash-java-rate-limiter-7.17.17.jar` to logstash's `/logstash-core/lib/jars/` directory.

# How to use

plugin `org.logstash.plugins.filters.RateLimitFilter`

|        param        |  type  | required | default value |              eg               |                                                    desc                                                     |
|:-------------------:|:------:|:--------:|:-------------:|:-----------------------------:|:-----------------------------------------------------------------------------------------------------------:|
|      rate_path      | string |    no    |     none      | /usr/share/logstash/rate.txt  | Get the rate from this text file path, using only the first line. You can change the value in it if needed. |
|     count_path      | string |    no    |     none      | /usr/share/logstash/count.txt |                                  Record the count of events in this path.                                   |
| count_log_delay_sec |  long  |    no    |      30       |              30               |                Record the count of events in the Logstash log with a fixed delay in seconds.                |

eg.

```shell
input {
  elasticsearch {
    hosts => "http://xxx-es.xxx.com:9200"
    index => "xxx"
    user => "elastic"
    password => "XXXX"
    query => '{ "query": { "query_string": { "query": "*" } } }'
    size => 2000
    scroll => "10m"
    docinfo => true
    # docinfo_fields => ["_index", "_id", "_type", "_routing"]
  }
}


filter {
  # plugin name
  java_rate_limit {
    # Set the rate in the first line of this text.
    rate_path => "/usr/share/logstash/rate.txt"
    # Recording the event count in this path, not required.
    count_path => "/usr/share/logstash/count.txt"
    # To fix the delay in recording the event count in the log file
    count_log_delay_sec => 30
  }
}


output {
  elasticsearch {
   hosts => "yyy-es.yyy.com:9200"
    index => "xxx"
    user => "elastic"
    password => "YYYY"
    document_id => "%{[@metadata][_id]}"
    # document_type => "%{[@metadata][_type]}"
    # routing => "%{[@metadata][_routing]}"
  }
}

```