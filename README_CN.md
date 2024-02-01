# logstash限流Filter插件

一个简单的Java Filter插件，使用Guava的RateLimiter进行限流.  
你可以轻松地通过修改文本的值来修改限流值，  
不需要重新启动Logstash,限流值会在1s内起作用。

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

# 如何编译

- 请复制logstash-core.jar到当前目录的bin文件夹下

```
logstash-java-rate-limiter
 |_ bin
    |_  install-logstash-core.sh 
    |_  logstash-core.jar
```

- 进入bin目录并执行脚本，将logstash-core安装到本地

```shell
cd bin
sh ./install-logstash-core.sh
```

- 使用JDK8来编译logstash-java-rate-limiter

```
mvn clean install -DskipTests
```

- 将target文件夹中的`logstash-java-rate-limiter-7.17.17.jar`复制到logstash的`/logstash-core/lib/jars/`文件夹中.

# 如何使用

plugin `org.logstash.plugins.filters.RateLimitFilter`

|        param        |  type  | required | 默认值 |              样例               |               desc               |
|:-------------------:|:------:|:--------:|:---:|:-----------------------------:|:--------------------------------:|
|      rate_path      | string |    no    |  无  | /usr/share/logstash/rate.txt  | 从该文件中读取第一行作为限流值，你可以随时修改这个文件中的限流值 |
|     count_path      | string |    no    |  无  | /usr/share/logstash/count.txt |        记录已经同步的事件的数量到该文件中         |
| count_log_delay_sec |  long  |    no    | 30  |              30               | 根据设置的秒数以固定间隔在logstash的日志中打印事件数量  |

样例

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
    # 设置限流值到该文件的第一行
    rate_path => "/usr/share/logstash/rate.txt"
    # 用于记录时间的数量的文件
    count_path => "/usr/share/logstash/count.txt"
    #  根据设置的秒数定时打印事件数量到日志中
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