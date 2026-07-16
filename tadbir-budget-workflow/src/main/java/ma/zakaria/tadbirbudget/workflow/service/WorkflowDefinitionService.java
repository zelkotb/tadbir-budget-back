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
package ma.zakaria.tadbirbudget.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.workflow.dto.DeploymentResult;
import ma.zakaria.tadbirbudget.workflow.dto.ProcessDefinitionDto;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Create / update / inspect BPMN process definitions through Flowable's RepositoryService.
 * "Updating" a workflow = deploying a new version of the same BPMN process id — running
 * instances keep the version they started on (exactly like the old engine, but native).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {

    private final RepositoryService repositoryService;

    /** Deploy a BPMN file (create a process, or a new version of an existing one). */
    @Transactional
    public DeploymentResult deploy(String name, MultipartFile file) {
        try {
            Deployment deployment = repositoryService.createDeployment()
                    .name(name != null && !name.isBlank() ? name : file.getOriginalFilename())
                    .addInputStream(bpmnResourceName(file.getOriginalFilename()), file.getInputStream())
                    .deploy();
            List<ProcessDefinitionDto> definitions = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId()).list().stream()
                    .map(ProcessDefinitionDto::from).toList();
            log.info("Deployed BPMN deployment={} definitions={}", deployment.getId(), definitions.size());
            return new DeploymentResult(deployment.getId(), definitions);
        } catch (IOException ex) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (FlowableException ex) {
            // invalid / unparseable BPMN
            log.warn("BPMN deployment rejected: {}", ex.getMessage());
            throw new CustomException(ErrorCode.INVALID_REQUEST_BODY, HttpStatus.BAD_REQUEST);
        }
    }

    /** Latest version of every deployed process. */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionDto> listLatest() {
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion().orderByProcessDefinitionKey().asc().list()
                .stream().map(ProcessDefinitionDto::from).toList();
    }

    /** All versions of one process (by BPMN process id). */
    @Transactional(readOnly = true)
    public List<ProcessDefinitionDto> listVersions(String processKey) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey).orderByProcessDefinitionVersion().asc().list()
                .stream().map(ProcessDefinitionDto::from).toList();
    }

    /** The raw BPMN XML of one definition (for a visual viewer / editor). */
    @Transactional(readOnly = true)
    public String getBpmnXml(String definitionId) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId).singleResult();
        if (def == null) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        try (InputStream in = repositoryService.getResourceAsStream(def.getDeploymentId(), def.getResourceName())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Flowable needs the resource name to end with .bpmn20.xml or .bpmn to parse it. */
    private String bpmnResourceName(String original) {
        if (original != null && (original.endsWith(".bpmn20.xml") || original.endsWith(".bpmn"))) {
            return original;
        }
        String base = (original == null || original.isBlank()) ? "process" : original;
        return base + ".bpmn20.xml";
    }
}
