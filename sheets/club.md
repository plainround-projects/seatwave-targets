# SeatWave requirements sheet: the club, version 1.0

Signed off by: Club owner. Date: 10 November 2026.
Assumptions: 200 users a day, 200 seats a night, one developer, 50 USD a month (Module 1).

| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner | Reason |
|---|---|---|---|---|---|---|---|
| All operations | Latency | Response time at the server | p99 | under 1,000 ms | 30-day month of 43,200 minutes | Developer | Regulars book from their phones, and a second is fine |
| All operations | Throughput | Completed requests per second | peak | at least 7 rps | Busiest 10 minutes of a day | Developer | 200 users x 20 requests in 10 minutes (Module 1) |
| All operations | Availability | Share of good minutes | rate | at least 99 % | 30-day month of 43,200 minutes | Club owner | 7.2 hours a month is acceptable, since an outage at 03:00 costs nothing |
| Hold a seat | Reliability | Seats with more than one active hold or booking | count | 0 | Every night, checked next morning | Developer | 200 seats, and a double booking is an argument at the door |
| All operations | Efficiency | Hosting cost per month | max | 50 USD | Calendar month | Club owner | The whole software budget of the club |
| All operations | Maintainability | Time from merged change to production | max | 1 working day | Rolling 30 days | Developer | One developer, no on-call, and nothing is urgent at this size |
