/**
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.gowk.cas.client.redis;


import org.jasig.cas.client.session.SessionMappingStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis backed implementation of SessionMappingStorage.
 *
 * @author zhangtf
 * @version $Revision$ $Date$
 * @since 3.1
 *
 */
//@Component
public final class RedisBackedSessionMappingStorage implements SessionMappingStorage {

    /**
     * Maps the ID from the CAS server to the Session.
     */
    private final Map<String, HttpSession> MANAGED_SESSIONS = new HashMap<String, HttpSession>();

    /**
     * Maps the Session ID to the key from the CAS Server.
     */
    private final Map<String, String> ID_TO_SESSION_KEY_MAPPING = new HashMap<String, String>();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final static String CASCLIENT_PREFIX = "CASCLI:SESSIONID:";
    private final static String CASCLIENT_MAPID_PREFIX = "CASCLI:MAPID:";

    private int casTimeout = 86400;

    private RedisTemplate redisTemplate;

    public RedisBackedSessionMappingStorage(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public synchronized void addSessionById(String mappingId, HttpSession session) {
        logger.debug("Adding ticket {}", session);
        try {
            //redisTemplate = (RedisTemplate) SpringUtil.getBean("redisTemplate");
            String sessionRedisKey = this.getCasSessionRedisKey(session.getId());
            String mappingIdRedisKey = this.getCasMappingIdRedisKey(mappingId);
            this.redisTemplate.boundValueOps(sessionRedisKey).set(mappingId, casTimeout, TimeUnit.SECONDS);
            this.redisTemplate.boundValueOps(mappingIdRedisKey).set(session.getId(), casTimeout, TimeUnit.SECONDS);
        } catch (final Exception e) {
            logger.error("Failed Adding {}", session, e);
        }
    }

    @Override
    public synchronized void removeBySessionById(String sessionId) {
        logger.debug("Attempting to remove Session=[{}]", sessionId);

        final String key = (String) this.redisTemplate.boundValueOps(this.getCasSessionRedisKey(sessionId)).get();

        if (logger.isDebugEnabled()) {
            if (key != null) {
                logger.debug("Found mapping for session.  Session Removed.");
            } else {
                logger.debug("No mapping for session found.  Ignoring.");
            }
        }
        this.redisTemplate.delete(this.getCasMappingIdRedisKey(key));
        this.redisTemplate.delete(this.getCasSessionRedisKey(sessionId));
        this.redisTemplate.delete(this.getSpringSessionRedisKey(sessionId));
        this.redisTemplate.delete(this.getSpringSessionExpiresRedisKey(sessionId));
    }

    @Override
    public synchronized HttpSession removeSessionByMappingId(String mappingId) {
        //先去取sessionid
        String casMappingIdRedisKey = this.getCasMappingIdRedisKey(mappingId);
        if (casMappingIdRedisKey != null) {
            return null;
        }
        logger.debug("casMappingIdRedisKey:" + casMappingIdRedisKey);
        final String sessionId = (String) this.redisTemplate.boundValueOps(casMappingIdRedisKey).get();
        logger.debug("sessionId:" + sessionId);
        //final HttpSession session = (HttpSession) this.redisTemplate.boundValueOps(sessionId).get();
        Boolean deleteSessionId = this.redisTemplate.delete(sessionId);
        logger.debug("deleteSessionId:" + deleteSessionId);
//	        if (session != null) {
//	            removeBySessionById(session.getId());
//	        }
//	        return session;
        if (sessionId != null) {
            removeBySessionById(sessionId);
        }
        return null;
    }

    private String getCasSessionRedisKey(String sessionId) {
        return CASCLIENT_PREFIX + sessionId;
    }

    private String getSpringSessionRedisKey(String sessionId) {
        return "spring:session:sessions:" + sessionId;
    }

    private String getSpringSessionExpiresRedisKey(String sessionId) {
        return "spring:session:sessions:expires:" + sessionId;
    }

    private String getCasMappingIdRedisKey(String mappingId) {
        return CASCLIENT_MAPID_PREFIX + mappingId;
    }
}
