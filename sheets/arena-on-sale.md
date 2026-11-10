# SeatWave requirements sheet: arena on-sale, version 1.0

Signed off by: Venue sales director (business owner). Date: 10 November 2026.
Scope of every measurement: at the load balancer, so the fan's own device and network are outside it.
Sale window: 10:00:00 to 10:15:00 on the day of the on-sale.
A good request: a correct status (2xx, or 409 for a seat already taken) inside the latency threshold of its operation.

| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner | Reason |
|---|---|---|---|---|---|---|---|
| List events | Latency | Response time at the load balancer | p99 | under 500 ms | Sale window, 10:00 to 10:15 | Platform lead | It is the first page a fan sees, and a slow list delays every later step |
| List events | Availability | Share of good requests (2xx within 500 ms) | rate | at least 99.95 % | Sale window, 10:00 to 10:15 | Platform lead | A fan who cannot open the event cannot buy |
| View seats | Latency | Response time at the load balancer | p50 | under 100 ms | Sale window, 10:00 to 10:15 | Platform lead | The typical fan redraws the seat map three times in the first minute |
| View seats | Latency | Response time at the load balancer | p99 | under 500 ms | Sale window, 10:00 to 10:15 | Platform lead | A stale, slow map makes fans press hold on seats that are already taken |
| View seats | Throughput | Completed requests per second | peak | at least 20,000 rps | First 60 seconds of the sale window | Platform lead | 200,000 fans x 3 seat maps in 60 s, peak factor 2 (Module 1) |
| View seats | Availability | Share of good requests (2xx within 500 ms) | rate | at least 99.95 % | Sale window, 10:00 to 10:15 | Platform lead | No seat map means no sale |
| Hold a seat | Latency | Response time at the load balancer | p50 | under 50 ms | Sale window, 10:00 to 10:15 | Booking lead | The typical hold must feel instant |
| Hold a seat | Latency | Response time at the load balancer | p95 | under 150 ms | Sale window, 10:00 to 10:15 | Booking lead | An early warning line between the typical hold and the worst allowed hold |
| Hold a seat | Latency | Response time at the load balancer | p99 | under 300 ms | Sale window, 10:00 to 10:15 | Booking lead | A hold that feels stuck is pressed again, which doubles the load (Module 1 budget) |
| Hold a seat | Throughput | Completed requests per second | peak | at least 13,333 rps | First 60 seconds of the sale window | Booking lead | 200,000 fans x 2 hold attempts in 60 s, peak factor 2 (Module 1) |
| Hold a seat | Availability | Share of good requests (201 or 409 within 300 ms) | rate | at least 99.95 % | Sale window, 10:00 to 10:15 | Booking lead | 400,000 attempts allow 200 failures, and each failure is a fan who may lose a seat |
| Hold a seat | Reliability | Seats with more than one active hold or booking | count | 0 | Every on-sale, checked within 1 hour of its end | Booking lead | One double booking means a refund, a fan turned away at the door and a press story |
| Hold a seat | Security | Active holds per customer | max | 4 | At all times | Booking lead | One script could otherwise hold all 20,000 seats (Module 1) |
| Confirm a booking | Latency | Response time at the load balancer, payment call included | p99 | under 2,000 ms | Sale window, 10:00 to 10:15 | Payments lead | The payment provider's call dominates, and fans accept a short wait at the pay step |
| Confirm a booking | Throughput | Completed requests per second | peak | at least 200 rps | First 5 minutes of the sale window | Payments lead | 20,000 seats confirmed inside the 300-second hold, peak factor 3 |
| Confirm a booking | Availability | Share of good requests (201 or 409 within 2,000 ms) | rate | at least 99.8 % | Sale window, 10:00 to 10:15 | Payments lead | The provider commits to 99.9 %, and a failed confirm can be retried inside the 300-second hold |
| Confirm a booking | Reliability | Bookings without a matching successful payment | count | 0 | Every on-sale, reconciled within 24 hours | Payments lead | A seat given away, or a fan charged for nothing, is a refund and a complaint |
| Confirm a booking | Compliance | Time each booking record is kept | min | 7 years | From the date of the event | Legal counsel | Record-keeping rules for the venue (assumed 7 years as in Module 1, and legal confirms) |
| All operations | Availability | Share of good minutes | rate | at least 99.95 % | 30-day month of 43,200 minutes | Platform lead | The venue contract allows 21.6 bad minutes a month, and listings run all month |
| All operations | Scalability | Cost per thousand requests at 30,000 rps divided by the same cost at 3,000 rps | ratio | at most 1.0 | Each load test before an on-sale | Platform lead | Cost must grow no faster than load, or the next tour is unaffordable |
| All operations | Efficiency | Cost per thousand requests | mean | at most 1.50 USD | Calendar month with an on-sale | Finance owner | A budget of 20,000 USD over about 14 million requests |
| All operations | Maintainability | Time from merged change to production | p90 | under 60 minutes | Rolling 30 days, outside the 24-hour freeze | Platform lead | A defect found in the rehearsal must be fixed before the sale |
| All operations | Maintainability | Time to recover from a failed part | mean | under 30 seconds | Sale window, 10:00 to 10:15 | Platform lead | A 90-second restart (Module 1) is 10 percent of a 15-minute window |
| All operations | Privacy | Days that personal data of an unconfirmed hold is kept | max | 30 days | Continuous, checked monthly | Data protection officer | Keep only what a booking needs, because less data is less to leak |
