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
package ma.zakaria.tadbirbudget.project.service;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.entity.OrgUnit;
import ma.zakaria.tadbirbudget.entity.Project;
import ma.zakaria.tadbirbudget.entity.ProjectDocument;
import ma.zakaria.tadbirbudget.entity.ProjectMember;
import ma.zakaria.tadbirbudget.entity.ProjectPhase;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.entity.enums.PhaseStatus;
import ma.zakaria.tadbirbudget.entity.enums.ProjectStatus;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.files.dto.StoredFile;
import ma.zakaria.tadbirbudget.files.service.FileStorageService;
import ma.zakaria.tadbirbudget.project.dto.CreateProjectInput;
import ma.zakaria.tadbirbudget.project.dto.DocumentDownload;
import ma.zakaria.tadbirbudget.project.dto.ProjectDocumentResponse;
import ma.zakaria.tadbirbudget.project.dto.ProjectMemberInput;
import ma.zakaria.tadbirbudget.project.dto.ProjectMemberResponse;
import ma.zakaria.tadbirbudget.project.dto.ProjectResponse;
import ma.zakaria.tadbirbudget.project.dto.SetTeamInput;
import ma.zakaria.tadbirbudget.project.dto.UpdateProjectInput;
import ma.zakaria.tadbirbudget.repository.OrgUnitRepository;
import ma.zakaria.tadbirbudget.repository.ProjectDocumentRepository;
import ma.zakaria.tadbirbudget.repository.ProjectMemberRepository;
import ma.zakaria.tadbirbudget.repository.ProjectPhaseRepository;
import ma.zakaria.tadbirbudget.repository.ProjectRepository;
import ma.zakaria.tadbirbudget.repository.UserRepository;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Projects / programs. Authorization goes through a single checkpoint — {@link #authorizeScope} —
 * which today means "a manager role acting within their own org subtree (admin anywhere)"; a future
 * delegation feature extends only that one method. Reads are scoped to the caller's subtree, except
 * ADMIN / DIRECTION_GENERALE / CONTROLE_GESTION (who supervise everything) and a project's own
 * chef / members. Every change is Envers-audited to its actor.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    /** Storage sub-directory for project documents (one folder per project underneath). */
    private static final String DOCUMENTS_SUBDIR = "project-documents";

    private final ProjectRepository         projectRepository;
    private final ProjectMemberRepository   memberRepository;
    private final ProjectDocumentRepository documentRepository;
    private final ProjectPhaseRepository    phaseRepository;
    private final UserRepository            userRepository;
    private final OrgUnitRepository          orgUnitRepository;
    private final FileStorageService        fileStorageService;

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(ProjectStatus status, UUID orgUnitId) {
        User caller = currentUser();
        List<Project> base;
        if (seesAll(caller)) {
            base = projectRepository.findAllByOrderByNameAsc();
        } else {
            OrgUnit callerUnit = caller.getOrgUnitId() == null ? null
                    : orgUnitRepository.findById(caller.getOrgUnitId()).orElse(null);
            if (callerUnit == null) {
                return List.of();
            }
            List<UUID> subtree = orgUnitRepository
                    .findByPathStartingWithOrderByPathAsc(callerUnit.getPath())
                    .stream().map(OrgUnit::getId).toList();
            base = projectRepository.findByOrgUnitIdInOrderByNameAsc(subtree);
        }
        List<Project> filtered = base.stream()
                .filter(p -> status == null || p.getStatus() == status)
                .filter(p -> orgUnitId == null || p.getOrgUnitId().equals(orgUnitId))
                .toList();
        return toResponses(filtered, false);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        Project p = load(id);
        authorizeRead(currentUser(), p);
        return toResponses(List.of(p), true).get(0);
    }

    // ── Shared access checkpoints (reused by sub-resources like phases) ─────────

    /** Load a project the current user may read (else ACCESS_DENIED / NOT_FOUND). */
    @Transactional(readOnly = true)
    public Project requireReadable(UUID projectId) {
        Project p = load(projectId);
        authorizeRead(currentUser(), p);
        return p;
    }

    /** Load a project the current user may manage — chef or manager-in-scope (else ACCESS_DENIED). */
    @Transactional(readOnly = true)
    public Project requireManageable(UUID projectId) {
        Project p = load(projectId);
        authorizeManage(currentUser(), p);
        return p;
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse create(CreateProjectInput input) {
        User caller = currentUser();
        requireUserExists(input.getChefProjetId());
        OrgUnit target = orgUnitRepository.findById(input.getOrgUnitId())
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_UNIT_NOT_FOUND, HttpStatus.BAD_REQUEST));
        authorizeScope(caller, target.getId());

        Project p = projectRepository.save(Project.builder()
                .name(input.getName().trim())
                .objectifs(trimToNull(input.getObjectifs()))
                .description(trimToNull(input.getDescription()))
                .status(ProjectStatus.NOT_STARTED)
                .chefProjetId(input.getChefProjetId())
                .orgUnitId(target.getId())
                .createdBy(SecurityUtils.getCurrentUsername())
                .createdAt(Instant.now())
                .build());
        writeTeam(p.getId(), input.getTeam());
        return toResponses(List.of(p), true).get(0);
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectInput input) {
        Project p = load(id);
        authorizeScope(currentUser(), p.getOrgUnitId());
        requireNotArchived(p);

        if (input.getName() != null)        p.setName(input.getName().trim());
        if (input.getObjectifs() != null)   p.setObjectifs(trimToNull(input.getObjectifs()));
        if (input.getDescription() != null) p.setDescription(trimToNull(input.getDescription()));
        if (input.getChefProjetId() != null) {
            requireUserExists(input.getChefProjetId());
            p.setChefProjetId(input.getChefProjetId());
        }
        return toResponses(List.of(projectRepository.save(p)), true).get(0);
    }

    @Transactional
    public ProjectResponse setTeam(UUID id, SetTeamInput input) {
        Project p = load(id);
        authorizeScope(currentUser(), p.getOrgUnitId());
        requireNotArchived(p);
        memberRepository.deleteByProjectId(id);
        memberRepository.flush();
        writeTeam(id, input.getTeam());
        return toResponses(List.of(p), true).get(0);
    }

    /**
     * Start a project: NOT_STARTED → ACTIVE, recording the start date (defaults to today).
     * Allowed for the responsible — the chef de projet — or a project-creator manager in scope.
     */
    @Transactional
    public ProjectResponse start(UUID id, LocalDate startDate) {
        Project p = load(id);
        authorizeManage(currentUser(), p);
        if (p.getStatus() != ProjectStatus.NOT_STARTED) {
            throw new CustomException(ErrorCode.PROJECT_INVALID_STATUS, HttpStatus.CONFLICT);
        }
        p.setStatus(ProjectStatus.ACTIVE);
        p.setStartDate(startDate != null ? startDate : LocalDate.now());
        return toResponses(List.of(projectRepository.save(p)), true).get(0);
    }

    @Transactional
    public ProjectResponse terminate(UUID id, LocalDate terminationDate) {
        Project p = load(id);
        authorizeScope(currentUser(), p.getOrgUnitId());
        if (p.getStatus() != ProjectStatus.ACTIVE) {
            throw new CustomException(ErrorCode.PROJECT_INVALID_STATUS, HttpStatus.CONFLICT);
        }
        List<ProjectPhase> phases = phaseRepository.findByProjectId(id);
        // Every phase must be closed (terminated or cancelled) before the project can be terminated.
        if (phases.stream().anyMatch(ph -> ph.getStatus() != PhaseStatus.TERMINATED
                && ph.getStatus() != PhaseStatus.CANCELLED)) {
            throw new CustomException(ErrorCode.PROJECT_HAS_OPEN_PHASES, HttpStatus.CONFLICT);
        }
        // The project cannot end before its last (non-cancelled) phase ends.
        LocalDate lastPhaseEnd = phases.stream()
                .filter(ph -> ph.getStatus() != PhaseStatus.CANCELLED)
                .map(ProjectPhase::getEndDate)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (lastPhaseEnd != null && terminationDate.isBefore(lastPhaseEnd)) {
            throw new CustomException(ErrorCode.PROJECT_TERMINATION_BEFORE_PHASE_END, HttpStatus.CONFLICT);
        }
        p.setStatus(ProjectStatus.TERMINATED);
        p.setTerminationDate(terminationDate);
        return toResponses(List.of(projectRepository.save(p)), true).get(0);
    }

    @Transactional
    public ProjectResponse archive(UUID id) {
        Project p = load(id);
        authorizeScope(currentUser(), p.getOrgUnitId());
        if (p.getStatus() == ProjectStatus.ARCHIVED) {
            throw new CustomException(ErrorCode.PROJECT_INVALID_STATUS, HttpStatus.CONFLICT);
        }
        p.setStatus(ProjectStatus.ARCHIVED);
        return toResponses(List.of(projectRepository.save(p)), true).get(0);
    }

    @Transactional
    public void delete(UUID id) {
        Project p = load(id);
        authorizeScope(currentUser(), p.getOrgUnitId());
        List<ProjectDocument> docs = documentRepository.findByProjectId(id);  // capture paths before cascade
        memberRepository.deleteByProjectId(id);
        projectRepository.delete(p);   // FK on delete cascade removes project_document rows
        docs.forEach(d -> fileStorageService.delete(d.getFilePath()));
    }

    // ── Documents ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectDocumentResponse> listDocuments(UUID projectId) {
        Project p = load(projectId);
        authorizeRead(currentUser(), p);
        return documentRepository.findByProjectIdOrderByUploadedAtDesc(projectId).stream()
                .map(ProjectDocumentResponse::from).toList();
    }

    /** Attach a file (any type) to the project. Chef / manager-in-scope only. */
    @Transactional
    public ProjectDocumentResponse uploadDocument(UUID projectId, MultipartFile file, String label) {
        Project p = load(projectId);
        authorizeManage(currentUser(), p);
        requireNotArchived(p);
        StoredFile stored = fileStorageService.store(file, DOCUMENTS_SUBDIR + "/" + projectId);
        ProjectDocument doc = documentRepository.save(ProjectDocument.builder()
                .projectId(projectId)
                .filePath(stored.path())
                .originalFileName(stored.originalFileName())
                .contentType(stored.contentType())
                .sizeBytes(stored.sizeBytes())
                .label(trimToNull(label))
                .uploadedBy(SecurityUtils.getCurrentUsername())
                .uploadedAt(Instant.now())
                .build());
        return ProjectDocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadDocument(UUID projectId, UUID documentId) {
        Project p = load(projectId);
        authorizeRead(currentUser(), p);
        ProjectDocument doc = loadDocument(projectId, documentId);
        Resource resource = fileStorageService.load(doc.getFilePath());
        String contentType = doc.getContentType() != null ? doc.getContentType()
                : fileStorageService.probeContentType(doc.getFilePath());
        return new DocumentDownload(resource, doc.getOriginalFileName(), contentType);
    }

    /** Remove a document (row + stored bytes). Chef / manager-in-scope only. */
    @Transactional
    public void deleteDocument(UUID projectId, UUID documentId) {
        Project p = load(projectId);
        authorizeManage(currentUser(), p);
        ProjectDocument doc = loadDocument(projectId, documentId);
        documentRepository.delete(doc);
        fileStorageService.delete(doc.getFilePath());
    }

    private ProjectDocument loadDocument(UUID projectId, UUID documentId) {
        ProjectDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!doc.getProjectId().equals(projectId)) {   // document must belong to the path's project
            throw new CustomException(ErrorCode.PROJECT_DOCUMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return doc;
    }

    // ── Authorization (the single checkpoint delegation will extend) ────────────

    /** Manager acting within their own org subtree (admin anywhere). Throws ACCESS_DENIED otherwise. */
    private void authorizeScope(User caller, UUID targetOrgUnitId) {
        if (caller.getRoles().contains(Roles.ADMIN)) {
            return;
        }
        if (caller.getOrgUnitId() == null) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        OrgUnit callerUnit = orgUnitRepository.findById(caller.getOrgUnitId())
                .orElseThrow(() -> new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN));
        OrgUnit target = orgUnitRepository.findById(targetOrgUnitId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORG_UNIT_NOT_FOUND, HttpStatus.BAD_REQUEST));
        if (!target.getPath().startsWith(callerUnit.getPath())) {   // target is caller's unit or below
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Who may perform a project's responsible actions — lifecycle (start) and destructive document
     * writes (upload / delete): its chef de projet (the responsible — whatever their role), or a
     * project-creator manager acting within scope (admin anywhere). Team members and pure read-scope
     * viewers may see the project but not write to it. Throws ACCESS_DENIED otherwise.
     */
    private void authorizeManage(User caller, Project p) {
        if (p.getChefProjetId().equals(caller.getId())) {   // the responsible
            return;
        }
        if (!isProjectCreator(caller)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        authorizeScope(caller, p.getOrgUnitId());
    }

    /** Roles allowed to create/manage projects (mirrors {@link Roles#IS_PROJECT_CREATOR}). */
    private boolean isProjectCreator(User caller) {
        return caller.getRoles().contains(Roles.ADMIN)
                || caller.getRoles().contains(Roles.CELL_MANAGER)
                || caller.getRoles().contains(Roles.SERVICE_MANAGER)
                || caller.getRoles().contains(Roles.DEPARTMENT_MANAGER)
                || caller.getRoles().contains(Roles.DIRECTION_MANAGER)
                || caller.getRoles().contains(Roles.POLE_MANAGER);
    }

    private void authorizeRead(User caller, Project p) {
        if (seesAll(caller)) {
            return;
        }
        if (caller.getOrgUnitId() != null) {
            OrgUnit callerUnit = orgUnitRepository.findById(caller.getOrgUnitId()).orElse(null);
            OrgUnit target = orgUnitRepository.findById(p.getOrgUnitId()).orElse(null);
            if (callerUnit != null && target != null && target.getPath().startsWith(callerUnit.getPath())) {
                return;
            }
        }
        if (p.getChefProjetId().equals(caller.getId())
                || memberRepository.existsByProjectIdAndUserId(p.getId(), caller.getId())) {
            return;
        }
        throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
    }

    /** Supervisory / global read: admin, direction générale, contrôle de gestion. */
    private boolean seesAll(User caller) {
        return caller.getRoles().contains(Roles.ADMIN)
                || caller.getRoles().contains(Roles.DIRECTION_GENERALE)
                || caller.getRoles().contains(Roles.CONTROLE_GESTION);
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private Project load(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private User currentUser() {
        return (User) SecurityUtils.getCurrentUser();
    }

    private void requireUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
    }

    private void requireNotArchived(Project p) {
        if (p.getStatus() == ProjectStatus.ARCHIVED) {
            throw new CustomException(ErrorCode.PROJECT_INVALID_STATUS, HttpStatus.CONFLICT);
        }
    }

    /** Inserts the team, de-duplicating by user and validating each user exists. */
    private void writeTeam(UUID projectId, List<ProjectMemberInput> team) {
        if (team == null || team.isEmpty()) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (ProjectMemberInput m : team) {
            if (m.getUserId() == null || !seen.add(m.getUserId())) {
                continue;
            }
            requireUserExists(m.getUserId());
            memberRepository.save(ProjectMember.builder()
                    .projectId(projectId)
                    .userId(m.getUserId())
                    .functionLabel(trimToNull(m.getFunctionLabel()))
                    .build());
        }
    }

    private List<ProjectResponse> toResponses(List<Project> projects, boolean includeTeam) {
        if (projects.isEmpty()) {
            return List.of();
        }
        List<UUID> projectIds = projects.stream().map(Project::getId).toList();
        List<ProjectMember> members = memberRepository.findByProjectIdIn(projectIds);
        Map<UUID, List<ProjectMember>> membersByProject = members.stream()
                .collect(Collectors.groupingBy(ProjectMember::getProjectId));

        Set<UUID> userIds = new HashSet<>();
        projects.forEach(p -> userIds.add(p.getChefProjetId()));
        members.forEach(m -> userIds.add(m.getUserId()));
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, String> orgNames = orgUnitRepository
                .findAllById(projects.stream().map(Project::getOrgUnitId).toList())
                .stream().collect(Collectors.toMap(OrgUnit::getId, OrgUnit::getName, (a, b) -> a));

        return projects.stream().map(p -> {
            List<ProjectMember> mem = membersByProject.getOrDefault(p.getId(), List.of());
            List<ProjectMemberResponse> team = includeTeam ? mem.stream().map(m -> {
                User u = users.get(m.getUserId());
                return new ProjectMemberResponse(m.getUserId(),
                        u == null ? null : u.getUid(), u == null ? null : u.getFullName(),
                        m.getFunctionLabel());
            }).toList() : null;
            User chef = users.get(p.getChefProjetId());
            return ProjectResponse.from(p, chef == null ? null : chef.getFullName(),
                    orgNames.get(p.getOrgUnitId()), mem.size(), team);
        }).toList();
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
