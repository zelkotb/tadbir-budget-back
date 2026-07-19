/*
 * Copyright (c) 2026 Zakaria El Kotb. All rights reserved.
 *
 * This source code is the exclusive property of Zakaria El Kotb.
 * Unauthorized copying, modification, distribution, or use of this file,
 * via any medium, is strictly prohibited without the prior written
 * permission of the copyright owner.
 *
 * Author: Zakaria El Kotb <elkotbzakaria@gmail.com>
 */
package ma.zakaria.tadbirbudget.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.zakaria.tadbirbudget.audit.CustomRevisionListener;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.io.Serializable;

/**
 * Custom Envers revision entity.
 *
 * The revision ID must be a numeric type — Hibernate Envers uses it
 * internally for ordering and comparison. UUID is not supported here.
 *
 * Uses {@code GenerationType.IDENTITY} so PostgreSQL's SERIAL column
 * (created by Liquibase with autoIncrement) handles ID generation
 * without a separate sequence.
 */
@Getter
@Setter
@Entity
@Table(name = "revinfo")
@RevisionEntity(CustomRevisionListener.class)
public class RevInfo implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private int id;

    @RevisionTimestamp
    @Column(name = "timestamp", nullable = false)
    private long timestamp;

    /** Login identifier ({@code uid}) of the authenticated user who triggered the change, or "system". */
    @Column(name = "actor", length = 255)
    private String actor;

    /** Client IP address at the time of the change (from MDC, populated by MdcFilter). */
    @Column(name = "ip_address", length = 45)
    private String ip;
}