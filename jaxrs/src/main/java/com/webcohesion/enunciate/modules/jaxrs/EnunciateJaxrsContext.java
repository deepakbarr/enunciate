package com.webcohesion.enunciate.modules.jaxrs;

import com.webcohesion.enunciate.EnunciateContext;
import com.webcohesion.enunciate.api.InterfaceDescriptionFile;
import com.webcohesion.enunciate.api.resources.Resource;
import com.webcohesion.enunciate.api.resources.ResourceApi;
import com.webcohesion.enunciate.api.resources.ResourceGroup;
import com.webcohesion.enunciate.facets.FacetFilter;
import com.webcohesion.enunciate.module.EnunciateModuleContext;
import com.webcohesion.enunciate.modules.jaxrs.api.impl.PathBasedResourceGroupImpl;
import com.webcohesion.enunciate.modules.jaxrs.api.impl.ResourceClassResourceGroupImpl;
import com.webcohesion.enunciate.modules.jaxrs.api.impl.ResourceImpl;
import com.webcohesion.enunciate.modules.jaxrs.model.ResourceMethod;
import com.webcohesion.enunciate.modules.jaxrs.model.RootResource;
import com.webcohesion.enunciate.modules.jaxrs.model.util.JaxrsUtil;
import org.apache.commons.configuration.HierarchicalConfiguration;

import javax.lang.model.element.TypeElement;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.*;

/**
 * @author Ryan Heaton
 */
@SuppressWarnings ( "unchecked" )
public class EnunciateJaxrsContext extends EnunciateModuleContext implements ResourceApi {

  public enum GroupingStrategy {
    path,
    resource_class
  }

  private final Map<String, String> mediaTypeIds;
  private final List<RootResource> rootResources;
  private final List<TypeElement> providers;
  private final Set<String> customResourceParameterAnnotations;
  private final Set<String> systemResourceParameterAnnotations;
  private String contextPath = "";
  private GroupingStrategy groupingStrategy = GroupingStrategy.resource_class;
  private InterfaceDescriptionFile wadlFile = null;
  private InterfaceDescriptionFile swaggerUI = null;

  public EnunciateJaxrsContext(EnunciateContext context) {
    super(context);
    this.mediaTypeIds = loadKnownMediaTypes();
    this.rootResources = new ArrayList<RootResource>();
    this.providers = new ArrayList<TypeElement>();
    this.customResourceParameterAnnotations = loadKnownCustomResourceParameterAnnotations(context);
    this.systemResourceParameterAnnotations = loadKnownSystemResourceParameterAnnotations(context);
  }

  protected Map<String, String> loadKnownMediaTypes() {
    HashMap<String, String> mediaTypes = new HashMap<String, String>();
    mediaTypes.put(MediaType.APPLICATION_ATOM_XML, "atom");
    mediaTypes.put(MediaType.APPLICATION_FORM_URLENCODED, "form");
    mediaTypes.put(MediaType.APPLICATION_JSON, "json");
    mediaTypes.put(MediaType.APPLICATION_OCTET_STREAM, "bin");
    mediaTypes.put(MediaType.APPLICATION_SVG_XML, "svg");
    mediaTypes.put(MediaType.APPLICATION_XHTML_XML, "xhtml");
    mediaTypes.put(MediaType.APPLICATION_XML, "xml");
    mediaTypes.put(MediaType.MULTIPART_FORM_DATA, "multipart");
    mediaTypes.put(MediaType.TEXT_HTML, "html");
    mediaTypes.put(MediaType.TEXT_PLAIN, "text");
    return mediaTypes;
  }

  protected Set<String> loadKnownCustomResourceParameterAnnotations(EnunciateContext context) {
    TreeSet<String> customResourceParameterAnnotations = new TreeSet<String>();

    //Jersey 1
    customResourceParameterAnnotations.add("com.sun.jersey.multipart.FormDataParam");

    //Jersey 2
    customResourceParameterAnnotations.add("org.glassfish.jersey.media.multipart.FormDataParam");

    //CXF
    customResourceParameterAnnotations.add("org.apache.cxf.jaxrs.ext.multipart.Multipart");

    //RESTEasy
    //(none?)

    //load the configured ones.
    List<HierarchicalConfiguration> configuredParameterAnnotations = context.getConfiguration().getSource().configurationsAt("modules/jaxrs/custom-resource-parameter-annotation");
    for (HierarchicalConfiguration configuredParameterAnnotation : configuredParameterAnnotations) {
      String fqn = configuredParameterAnnotation.getString("[@qualifiedName]", null);
      if (fqn != null) {
        customResourceParameterAnnotations.add(fqn);
      }
    }

    return customResourceParameterAnnotations;
  }

  protected Set<String> loadKnownSystemResourceParameterAnnotations(EnunciateContext context) {
    TreeSet<String> systemResourceParameterAnnotations = new TreeSet<String>();

    //JDK
    systemResourceParameterAnnotations.add("javax.inject.Inject");

    //Jersey
    systemResourceParameterAnnotations.add("com.sun.jersey.api.core.InjectParam");

    //CXF
    //(none?)

    //RESTEasy
    //(none?)

    //Spring
    systemResourceParameterAnnotations.add("org.springframework.beans.factory.annotation.Autowired");

    //load the configured ones.
    List<HierarchicalConfiguration> configuredSystemAnnotations = context.getConfiguration().getSource().configurationsAt("modules/jaxrs/custom-system-parameter-annotation");
    for (HierarchicalConfiguration configuredSystemAnnotation : configuredSystemAnnotations) {
      String fqn = configuredSystemAnnotation.getString("[@qualifiedName]", null);
      if (fqn != null) {
        systemResourceParameterAnnotations.add(fqn);
      }
    }

    return systemResourceParameterAnnotations;
  }

  public EnunciateContext getContext() {
    return context;
  }

  public Map<String, String> getMediaTypeIds() {
    //todo: configure media type ids?
    return mediaTypeIds;
  }

  /**
   * Add a content type.
   *
   * @param mediaType The content type to add.
   */
  public void addMediaType(String mediaType) {
    if (!mediaTypeIds.containsKey(mediaType)) {
      String id = getDefaultContentTypeId(mediaType);
      if (id != null) {
        mediaTypeIds.put(mediaType, id);
      }
    }
  }

  /**
   * Get the default content type id for the specified content type.
   *
   * @param contentType The content type.
   * @return The default content type id, or null if the content type is a wildcard type.
   */
  protected String getDefaultContentTypeId(String contentType) {
    String id = contentType;
    if (id.endsWith("/")) {
      throw new IllegalArgumentException("Illegal content type: " + id);
    }

    int semiColon = id.indexOf(';');
    if (semiColon > -1) {
      id = id.substring(0, semiColon);
    }

    int lastSlash = id.lastIndexOf('/');
    if (lastSlash > -1) {
      id = id.substring(lastSlash + 1);
    }

    int plus = id.indexOf('+');
    if (plus > -1) {
      id = id.substring(0, plus);
    }

    if (id.contains("*")) {
      //wildcard types have no ids.
      return null;
    }
    else {
      return id;
    }
  }

  public List<RootResource> getRootResources() {
    return rootResources;
  }

  public List<TypeElement> getProviders() {
    return providers;
  }

  public Set<String> getCustomResourceParameterAnnotations() {
    return this.customResourceParameterAnnotations;
  }

  public Set<String> getSystemResourceParameterAnnotations() {
    return this.systemResourceParameterAnnotations;
  }


  /**
   * Add a root resource to the model.
   *
   * @param rootResource The root resource to add to the model.
   */
  public void add(RootResource rootResource) {
    this.rootResources.add(rootResource);
    debug("Added %s as a JAX-RS root resource.", rootResource.getQualifiedName());
  }

  /**
   * Add a JAX-RS provider to the model.
   *
   * @param declaration The declaration of the provider.
   */
  public void addJAXRSProvider(TypeElement declaration) {
    this.providers.add(declaration);
    debug("Added %s as a JAX-RS provider.", declaration.getQualifiedName());

    Produces produces = declaration.getAnnotation(Produces.class);
    if (produces != null) {
      for (String contentType : JaxrsUtil.value(produces)) {
        try {
          MediaType mt = MediaType.valueOf(contentType);
          addMediaType(mt.getType() + "/" + mt.getSubtype());
        }
        catch (Exception e) {
          addMediaType(contentType);
        }
      }
    }

    Consumes consumes = declaration.getAnnotation(Consumes.class);
    if (consumes != null) {
      for (String contentType : JaxrsUtil.value(consumes)) {
        try {
          MediaType mt = MediaType.valueOf(contentType);
          addMediaType(mt.getType() + "/" + mt.getSubtype());
        }
        catch (Exception e) {
          addMediaType(contentType);
        }
      }
    }
  }

  @Override
  public boolean isIncludeResourceGroupName() {
    return false;
  }

  @Override
  public String getContextPath() {
    return this.contextPath;
  }

  public void setContextPath(String contextPath) {
    this.contextPath = contextPath;
  }

  public void setGroupingStrategy(GroupingStrategy groupingStrategy) {
    this.groupingStrategy = groupingStrategy;
  }

  @Override
  public InterfaceDescriptionFile getWadlFile() {
    return wadlFile;
  }

  public void setWadlFile(InterfaceDescriptionFile wadlFile) {
    this.wadlFile = wadlFile;
  }

  @Override
  public InterfaceDescriptionFile getSwaggerUI() {
    return swaggerUI;
  }

  public void setSwaggerUI(InterfaceDescriptionFile swaggerUI) {
    this.swaggerUI = swaggerUI;
  }

  @Override
  public List<ResourceGroup> getResourceGroups() {
    List<ResourceGroup> resourceGroups;
    if (this.groupingStrategy == GroupingStrategy.path) {
      //group resources by path.
      resourceGroups = getResourceGroupsByPath();
    }
    else {
      resourceGroups = getResourceGroupsByClass();
    }

    Collections.sort(resourceGroups, new Comparator<ResourceGroup>() {
      @Override
      public int compare(ResourceGroup o1, ResourceGroup o2) {
        return o1.getLabel().compareTo(o2.getLabel());
      }
    });
    return resourceGroups;
  }

  public List<ResourceGroup> getResourceGroupsByClass() {
    List<ResourceGroup> resourceGroups;
    resourceGroups = new ArrayList<ResourceGroup>();

    for (RootResource rootResource : rootResources) {
      ResourceGroup group = new ResourceClassResourceGroupImpl(rootResource, contextPath);

      if (!group.getResources().isEmpty()) {
        resourceGroups.add(group);
      }
    }
    return resourceGroups;
  }

  public List<ResourceGroup> getResourceGroupsByPath() {
    List<ResourceGroup> resourceGroups;Map<String, PathBasedResourceGroupImpl> resourcesByPath = new HashMap<String, PathBasedResourceGroupImpl>();

    FacetFilter facetFilter = context.getConfiguration().getFacetFilter();
    for (RootResource rootResource : rootResources) {
      for (ResourceMethod method : rootResource.getResourceMethods(true)) {
        if (facetFilter.accept(method)) {
          String path = method.getFullpath();
          PathBasedResourceGroupImpl resourceGroup = resourcesByPath.get(path);
          if (resourceGroup == null) {
            resourceGroup = new PathBasedResourceGroupImpl(contextPath, path, new ArrayList<Resource>());
            resourcesByPath.put(path, resourceGroup);
          }

          resourceGroup.getResources().add(new ResourceImpl(method, resourceGroup));
        }
      }
    }

    resourceGroups = new ArrayList<ResourceGroup>(resourcesByPath.values());
    return resourceGroups;
  }
}
