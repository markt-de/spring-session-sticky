[ ![Download](https://api.bintray.com/packages/markt-de/releases/spring-session-sticky/images/download.svg) ](https://bintray.com/markt-de/releases/spring-session-sticky/_latestVersion)

# Sticky sessions for spring-session

The [spring-session](https://github.com/spring-projects/spring-session/)
project has an 
[open issue](https://github.com/spring-projects/spring-session/issues/6) about
supporting the concept of sticky sessions, where a load-balancer is configured
to send one client always to the same server if possible.

This allows some optimizations like keeping a local cache of sessions, and
replicating changes to the centralized session store asynchronously.

This repository aims to provide a reference implementation of this feature,
which can ultimately be merged into spring-session upstream.

## Configuring StickySessionRepository

This project provides a `StickySessionRepository` class, which manages a local
cache of sessions and delegates to another repository for centralized session
management (e.g. `RedisIndexedSessionRepository`).

The simplest way to configure sticky sessions with a redis backend is by using
the `@EnableStickyRedisHttpSession` annotation: 

```java
@Configuration
@EnableStickyRedisHttpSession
public class StickySessionConfig {

  // here could be your redis connection factory

}

```

For a more advanced configuration, you can use `@EnableStickyHttpSession` and
provide your own adapter, or subclass `StickyHttpSessionConfiguration`.
