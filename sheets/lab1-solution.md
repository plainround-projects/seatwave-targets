# Lab 1 solution: one latency and one availability target for each of the four operations

| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner | Reason |
|---|---|---|---|---|---|---|---|
| List events | Latency | Response time at the load balancer | p99 | under 500 ms | Sale window, 10:00 to 10:15 | Platform lead | It is the first page a fan sees |
| List events | Availability | Share of good requests (2xx within 500 ms) | rate | at least 99.95 % | Sale window, 10:00 to 10:15 | Platform lead | A fan who cannot open the event cannot buy |
| View seats | Latency | Response time at the load balancer | p99 | under 500 ms | Sale window, 10:00 to 10:15 | Platform lead | A slow map makes fans press hold on taken seats |
| View seats | Availability | Share of good requests (2xx within 500 ms) | rate | at least 99.95 % | Sale window, 10:00 to 10:15 | Platform lead | No seat map means no sale |
| Hold a seat | Latency | Response time at the load balancer | p99 | under 300 ms | Sale window, 10:00 to 10:15 | Booking lead | A hold that feels stuck is pressed again |
| Hold a seat | Availability | Share of good requests (201 or 409 within 300 ms) | rate | at least 99.95 % | Sale window, 10:00 to 10:15 | Booking lead | 400,000 attempts allow 200 failures |
| Confirm a booking | Latency | Response time at the load balancer, payment call included | p99 | under 2,000 ms | Sale window, 10:00 to 10:15 | Payments lead | The payment call dominates the pay step |
| Confirm a booking | Availability | Share of good requests (201 or 409 within 2,000 ms) | rate | at least 99.8 % | Sale window, 10:00 to 10:15 | Payments lead | The provider commits to 99.9 %, and a confirm can be retried inside the hold |
