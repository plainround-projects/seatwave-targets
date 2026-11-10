# Lab 1 starting point: a first draft from a kick-off meeting

| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner | Reason |
|---|---|---|---|---|---|---|---|
| List events | Latency | Response time at the load balancer |  | under 500 ms | Sale window, 10:00 to 10:15 | Platform lead | It is the first page a fan sees |
| View seats | Latency | Response time at the load balancer | p99 | under 500 ms |  | Platform lead | A slow map makes fans press hold on taken seats |
| Hold a seat | Latency | Response time |  | fast |  |  |  |
| Confirm a booking | Availability | Share of good requests | rate | highly available | Sale window, 10:00 to 10:15 | TBD | Fans must be able to pay |
