/**
 * Copyright 2005-2015 Red Hat, Inc.
 * <p/>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.forge.devops;

import io.fabric8.devops.ProjectConfig;
import io.fabric8.devops.ProjectConfigs;
import io.fabric8.devops.connector.DevOpsConnector;
import io.fabric8.forge.addon.utils.CommandHelpers;
import io.fabric8.kubernetes.api.KubernetesClient;
import io.fabric8.letschat.LetsChatClient;
import io.fabric8.letschat.LetsChatKubernetes;
import io.fabric8.letschat.RoomDTO;
import io.fabric8.taiga.ProjectDTO;
import io.fabric8.taiga.TaigaClient;
import io.fabric8.taiga.TaigaKubernetes;
import io.fabric8.utils.Strings;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.facets.MetadataFacet;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.util.ResourceUtil;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.input.ValueChangeListener;
import org.jboss.forge.addon.ui.input.events.ValueChangeEvent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


public class ConfigureDevOpsStep extends AbstractDevOpsCommand implements UIWizardStep {
    private static final transient Logger LOG = LoggerFactory.getLogger(ConfigureDevOpsStep.class);

    @Inject
    @WithAttributes(label = "flow", required = false, description = "The URL of the Jenkins workflow groovy script to use for builds")
    private UIInput<String> flow;

    @Inject
    @WithAttributes(label = "chatRoom", required = false, description = "Name of chat room to use for this project")
    private UIInput<String> chatRoom;

    @Inject
    @WithAttributes(label = "issueProjectName", required = false, description = "Name of the issue tracker project")
    private UIInput<String> issueProjectName;

    @Inject
    @WithAttributes(label = "codeReview", required = false, description = "Enable code review of all commits")
    private UIInput<Boolean> codeReview;

    private KubernetesClient kubernetes;
    private LetsChatClient letsChat;
    private TaigaClient taiga;
    private List<InputComponent> inputComponents;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(getClass())
                .category(Categories.create(AbstractDevOpsCommand.CATEGORY))
                .name(AbstractDevOpsCommand.CATEGORY + ": Configure")
                .description("Configure the DevOps options for the new project");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        flow.setCompleter(new UICompleter<String>() {
            @Override
            public Iterable<String> getCompletionProposals(UIContext context, InputComponent<?, String> input, String value) {
                return getFlowURIs();
            }
        });
        flow.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                String value = event.getNewValue() != null ? event.getNewValue().toString() : null;
                if (value != null) {
                    String description = getDescriptionForFlow(value);
                    flow.setNote(description != null ? description : "");
                } else {
                    flow.setNote("");
                }
            }
        });
        chatRoom.setCompleter(new UICompleter<String>() {
            @Override
            public Iterable<String> getCompletionProposals(UIContext context, InputComponent<?, String> input, String value) {
                return getChatRoomNames();
            }
        });
        chatRoom.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                String value = event.getNewValue() != null ? event.getNewValue().toString() : null;
                if (value != null) {
                    String description = getDescriptionForChatRoom(value);
                    chatRoom.setNote(description != null ? description : "");
                } else {
                    chatRoom.setNote("");
                }
            }
        });
        issueProjectName.setCompleter(new UICompleter<String>() {
            @Override
            public Iterable<String> getCompletionProposals(UIContext context, InputComponent<?, String> input, String value) {
                return getIssueProjectNames();
            }
        });
        issueProjectName.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                String value = event.getNewValue() != null ? event.getNewValue().toString() : null;
                if (value != null) {
                    String description = getDescriptionForIssueProject(value);
                    issueProjectName.setNote(description != null ? description : "");
                } else {
                    issueProjectName.setNote("");
                }
            }
        });

        // lets initialise the data from the current config if it exists
        ProjectConfig config = null;
        Project project = getSelectedProject(builder.getUIContext());
        File configFile = getProjectConfigFile(project);
        if (configFile != null && configFile.exists()) {
            config = ProjectConfigs.parseProjectConfig(configFile);
        }
        if (config != null) {
            CommandHelpers.setInitialComponentValue(flow, config.firstFlow());
            CommandHelpers.setInitialComponentValue(chatRoom, config.getChatRoom());
            CommandHelpers.setInitialComponentValue(issueProjectName, config.getIssueProjectName());
            CommandHelpers.setInitialComponentValue(codeReview, config.getCodeReview());
        }

        inputComponents = CommandHelpers.addInputComponents(builder, flow, chatRoom, issueProjectName, codeReview);
    }


    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }


    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        LOG.info("Creating the fabric8.yml file");


        String fileName = ProjectConfigs.FILE_NAME;
        Project project = getSelectedProject(context);
        File configFile = getProjectConfigFile(project);
        if (configFile == null) {
            return Results.fail("This command requires a project");
        }
        ProjectConfig config = null;
        boolean hasFile = false;
        if (configFile.exists()) {
            config = ProjectConfigs.parseProjectConfig(configFile);
            hasFile = true;
        }
        if (config == null) {
            config = new ProjectConfig();
        }

        CommandHelpers.putComponentValuesInAttributeMap(context, inputComponents);
        updateConfiguration(context, config);
        System.out.println("Result: " + config);

        String message;
        if (config.isEmpty() && !hasFile) {
            message = "No " + fileName + " need be generated as there is no configuration";
            return Results.success(message);
        } else {
            String operation = "Updated";
            if (!configFile.exists()) {
                operation = "Created";
            }
            ProjectConfigs.saveConfig(config, configFile);
            message = operation + " " + fileName;
        }

        // now lets update the devops stuff
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();

        Object object = attributeMap.get(Project.class);
        if (object instanceof Project) {
            Project newProject = (Project) object;
            MetadataFacet facet = newProject.getFacet(MetadataFacet.class);
            if (facet != null) {
                String named = facet.getProjectName();

                String user = getStringAttribute(attributeMap, "gitUser");
                String email = getStringAttribute(attributeMap, "gitAuthorEmail");
                String address = getStringAttribute(attributeMap, "gitAddress");

                String htmlUrl = address + user + "/" + named;
                String remoteUrl = address + user + "/" + named + ".git";
                String fullName = user + "/" + named;
                String gitUrl = remoteUrl;

                DevOpsConnector connector = new DevOpsConnector();
                connector.setProjectConfig(config);
                connector.setUsername(user);
                connector.setPassword(getStringAttribute(attributeMap, "gitPassword"));
                connector.setBranch(getStringAttribute(attributeMap, "gitBranch", "master"));
                connector.setBasedir(getBaseDir(project));
                connector.setGitUrl(gitUrl);
                connector.setRepoName(named);
                connector.setTryLoadConfigFileFromRemoteGit(false);

                LOG.info("Using connector: " + connector);

/*
                TODO

                results.setOutputProperty("fullName", fullName);
                results.setOutputProperty("cloneUrl", remoteUrl);
                results.setOutputProperty("htmlUrl", htmlUrl);
*/

                try {
                    connector.execute();
                } catch (Exception e) {
                    LOG.error("Failed to update DevOps resources: " + e, e);
                }
            } else {
                LOG.error("No MetadataFacet for newly created project " + newProject);
            }
        } else {
            LOG.error("No New Project created!");
        }
        return Results.success(message);
    }

    protected String getStringAttribute(Map<Object, Object> attributeMap, String name, String defaultValue) {
        String answer = getStringAttribute(attributeMap, name);
        return Strings.isNullOrBlank(answer) ? defaultValue : answer;
    }

    protected String getStringAttribute(Map<Object, Object> attributeMap, String name) {
        Object value = attributeMap.get(name);
        if (value != null) {
            return value.toString();
        }
        return null;
    }

    protected String getDescriptionForFlow(String flow) {
        return null;
    }

    protected Iterable<String> getFlowURIs() {
        return Arrays.asList("maven/Deploy.groovy", "maven/DeployAndStage.groovy");
    }

    protected String getDescriptionForIssueProject(String value) {
        return null;
    }

    protected Iterable<String> getIssueProjectNames() {
        Set<String> answer = new TreeSet<>();
        try {
            TaigaClient letschat = getTaiga();
            if (letschat != null) {
                List<ProjectDTO> projects = null;
                try {
                    projects = letschat.getProjects();
                } catch (Exception e) {
                    LOG.warn("Failed to load chat projects! " + e, e);
                }
                if (projects != null) {
                    for (ProjectDTO project : projects) {
                        String name = project.getName();
                        if (name != null) {
                            answer.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to get issue project names: " + e, e);
        }
        return answer;

    }

    protected String getDescriptionForChatRoom(String chatRoom) {
        return null;
    }

    protected Iterable<String> getChatRoomNames() {
        Set<String> answer = new TreeSet<>();
        try {
            LetsChatClient letschat = getLetsChat();
            if (letschat != null) {
                List<RoomDTO> rooms = null;
                try {
                    rooms = letschat.getRooms();
                } catch (Exception e) {
                    LOG.warn("Failed to load chat rooms! " + e, e);
                }
                if (rooms != null) {
                    for (RoomDTO room : rooms) {
                        String name = room.getSlug();
                        if (name != null) {
                            answer.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to find chat room names: " + e, e);
        }
        return answer;
    }

    public KubernetesClient getKubernetes() {
        if (kubernetes == null) {
            kubernetes = new KubernetesClient();
        }
        return kubernetes;
    }

    public LetsChatClient getLetsChat() {
        if (letsChat == null) {
            letsChat = LetsChatKubernetes.createLetsChat(getKubernetes());
        }
        return letsChat;
    }

    public void setLetsChat(LetsChatClient letsChat) {
        this.letsChat = letsChat;
    }

    public TaigaClient getTaiga() {
        if (taiga == null) {
            taiga = TaigaKubernetes.createTaiga(getKubernetes());
        }
        return taiga;
    }

    public void setTaiga(TaigaClient taiga) {
        this.taiga = taiga;
    }


    public static File getProjectConfigFile(Project project) {
        if (project == null) {
            return null;
        }
        Resource<?> root = project.getRoot();
        if (root == null) {
            return null;
        }
        Resource<?> configFileResource = root.getChild(ProjectConfigs.FILE_NAME);
        if (configFileResource == null) {
            return null;
        }
        return ResourceUtil.getContextFile(configFileResource);
    }


    public static File getBaseDir(Project project) {
        if (project == null) {
            return null;
        }
        Resource<?> root = project.getRoot();
        if (root == null) {
            return null;
        }
        return ResourceUtil.getContextFile(root);
    }


    protected void updateConfiguration(UIExecutionContext context, ProjectConfig config) {
        Map<Object, Object> attributeMap = context.getUIContext().getAttributeMap();
        ProjectConfigs.configureProperties(config, attributeMap);
    }
}