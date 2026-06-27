package com.bigcorp.common.service;

import java.util.Hashtable;

/**
 * Service Locator - Core J2EE Pattern implementation.
 * 
 * In production this would use JNDI to look up EJBs, DataSources, 
 * and JMS ConnectionFactories from the app server. Since we don't
 * have an app server (yet -- the WebSphere migration is "coming soon"),
 * this is a simplified version that caches service instances.
 *
 * Every J2EE book says to use this pattern. So we did.
 * "Gang of Four would be proud" - Bob, 2001
 *
 * TODO: Add actual JNDI support when we get WebSphere
 * JIRA-1847: WebSphere migration (target date: Q3 2002)
 * (It's now Q4 2002 and we haven't started)
 *
 * @author Bob (original JNDI version)
 * @author Dave (simplified for standalone mode)
 * @since 1.2
 */
public class ServiceLocator {

    private static ServiceLocator instance;

    // Hashtable because "it's thread-safe" - Bob
    // Dave wanted to use HashMap but Bob said "Hashtable is synchronized,
    // HashMap isn't, do you WANT race conditions?"
    private Hashtable cache;

    // Service name constants (would be JNDI names in production)
    // These match what the WebSphere deployer would configure in the
    // JNDI tree. Or so we've been told. Nobody has actually seen the
    // WebSphere config document.
    public static final String DATABASE_SERVICE = "java:comp/env/jdbc/BigCorpDB";
    public static final String MQ_SERVICE = "java:comp/env/jms/BigCorpMQ";
    public static final String PRICING_SERVICE = "java:comp/env/ejb/PricingService";
    public static final String ORDER_SERVICE = "java:comp/env/ejb/OrderService";
    public static final String SETTLEMENT_SERVICE = "java:comp/env/ejb/SettlementService";

    /**
     * Private constructor - Singleton pattern.
     * If you call this directly you get what you deserve.
     */
    private ServiceLocator() {
        cache = new Hashtable();
    }

    /**
     * Get the singleton instance.
     * Synchronized because Bob read that double-checked locking is broken in Java.
     * He's right about that, actually. One of the few things.
     */
    public static synchronized ServiceLocator getInstance() {
        if (instance == null) {
            instance = new ServiceLocator();
            System.out.println("ServiceLocator initialized (standalone mode - no JNDI)");
        }
        return instance;
    }

    /**
     * Look up a service by JNDI name. Returns cached instance if available.
     * 
     * In the real JNDI version this would do:
     *   InitialContext ctx = new InitialContext();
     *   Object svc = ctx.lookup(serviceName);
     * 
     * But since we don't have an app server, it just checks the cache.
     * If the service isn't registered, returns null instead of throwing
     * NamingException like real JNDI would. Dave said "null is fine,
     * the caller can check." (The caller never checks.)
     *
     * @param serviceName the JNDI name of the service
     * @return the service instance, or null if not found
     */
    public Object lookup(String serviceName) {
        if (serviceName == null) {
            System.err.println("WARN: ServiceLocator.lookup() called with null name");
            return null;
        }

        Object service = cache.get(serviceName);
        if (service != null) {
            // cache hit - this is the whole point of the pattern
            return service;
        }

        // In JNDI mode we would do the lookup here and cache the result.
        // In standalone mode, the service must be registered first via register().
        // This is a "known limitation" per the design doc.
        System.out.println("ServiceLocator: cache miss for '" + serviceName + "'");
        System.out.println("  (Did you forget to call register()? In production JNDI handles this.)");
        return null;
    }

    /**
     * Register a service instance.
     * This is the standalone-mode replacement for JNDI binding.
     * In production, the app server would handle this.
     * In development, each module registers its services at startup.
     * 
     * "It's basically a fancy HashMap at this point" - Dave, 2002
     *
     * @param serviceName the JNDI-style name for the service
     * @param service the service instance to cache
     */
    public void register(String serviceName, Object service) {
        if (serviceName == null || service == null) {
            System.err.println("ERROR: ServiceLocator.register() called with null name or service");
            return;
        }
        cache.put(serviceName, service);
        System.out.println("ServiceLocator: registered '" + serviceName + "' -> " + service.getClass().getName());
    }

    /**
     * Clear the entire cache.
     * Used by the test team because "the singleton was leaking state between tests."
     * JIRA-2134: Test isolation issues with ServiceLocator
     */
    public void clearCache() {
        cache.clear();
        System.out.println("ServiceLocator: cache cleared");
    }

    /**
     * Check if a service is registered.
     * Added because Dave got tired of null checks after lookup().
     */
    public boolean isRegistered(String serviceName) {
        return cache.containsKey(serviceName);
    }

    /**
     * Get the number of cached services.
     * Used by the monitoring page (which nobody looks at).
     */
    public int getCacheSize() {
        return cache.size();
    }
}
