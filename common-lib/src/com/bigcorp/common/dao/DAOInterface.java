package com.bigcorp.common.dao;

import java.util.List;

/**
 * Generic DAO interface for CRUD operations.
 * 
 * Part of the Wave 11 DAO consolidation. Defines a standard
 * contract that all DAOs should implement over time.
 * 
 * @author architect
 * @since 2016-Q1
 */
public interface DAOInterface {

    /**
     * Find an entity by its primary key.
     * @return the entity, or null if not found
     */
    Object findById(String id);

    /**
     * List all entities (with optional limit).
     */
    List findAll();

    /**
     * Save (insert or update) an entity.
     * @return the number of rows affected
     */
    int save(Object entity);
}
