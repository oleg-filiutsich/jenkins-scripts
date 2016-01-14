import groovy.xml.*;
  
import hudson.model.*;
import hudson.maven.*;
import jenkins.model.*;
import jenkins.maven.*;
  
/**
 * For all enabled maven jobs tied to JDK 1.6:
 *   1) Disable and rename to DEPRECATED-X
 *   2) Create a new job with the original name, type freeStyleProject, rearranging the pre/maven/post into the main builders of the new job
 * 
 * Currently does not support folders (yet?)
 * 
 * USAGE: use as an "Execute System Groovy Script" step
 */
  
Jenkins.instance.items.findAll{job -> job instanceof MavenModuleSet && job.JDK && job.JDK.name.indexOf('1.6') > 0 && !job.isDisabled() }.each{
job ->
  println(job.name)
  oldName = job.name
  
  xml = getModifiedXml(job)
  
  println "Converted ${job.name}: \n${xml}\n\n"
  
  //rename old job
  job.renameTo("DEPRECATED-${job.name}")
  job.makeDisabled(true)
  
  //now 
  //push to a new job (can't overwrite with different job type)
  inputStream = new StringBufferInputStream(xml)
  Jenkins.instance.createProjectFromXML(oldName, inputStream)
  println "Renamed and converted ${oldName}"
  
}


def getModifiedXml(job) {
  def parser = new XmlParser()
  def response = parser.parse(job.getConfigFile().readRaw())
    
  
  builders = response.appendNode(new QName('builders'), [:])
  
  
  // Move prebuilders to builders
  response.prebuilders[0].children().each{ step ->
    builders.append(step)
    
  }
  response.remove(response.prebuilders[0])
  
  
  //Create a maven block
  mavenNode = builders.appendNode(
    new QName("hudson.tasks.Maven"),
    [:]
  )
  
  //mavenNode.appendNode(new QName('targets'), [:], response.goals[0].value())
  move(response.goals[0], mavenNode, 'targets')
  move(response.mavenName[0], mavenNode)
  if (response.rootPOM) {
  	move(response.rootPOM[0], mavenNode, 'pom')
  }
  if (response.mavenOpts) {
    move(response.mavenOpts[0], mavenNode, 'jvmOptions')
  }
  move(response.settings[0], mavenNode)
  move(response.globalSettings[0], mavenNode)
  
  //items that don't exist in maven step:
  remove(response, [
    'resolveDependencies', 
    'processPlugins', 
    'siteArchivingDisabled', 
    'archivingDisabled', 
    'mavenValidationLevel', 
    'disableTriggerDownstreamProjects', 
    'blockTriggerWhenBuilding', 
    'fingerprintingDisabled', 
    'incrementalBuild', 
    'processPlugins', 
    'siteArchivingDisabled', 
    'ignoreUpstremChanges', 
    'rootModule'
    ])
  
  
  
  // Move postbuilders to builders
  response.postbuilders[0].children().each{ step ->
    builders.append(step)
    
  }
  response.remove(response.postbuilders[0])
  
  
  //rename top-level element
  //new parent node
  newDoc = new groovy.util.Node(null, 'project')
  response.children().each{ c ->
    newDoc.append(c)
  }
  
  //println XmlUtil.serialize(newDoc)
  //println response.text() 
  
  return XmlUtil.serialize(newDoc)
}  


def remove(from, listOfNames) {
  //println from.children().findAll { listOfNames.contains(it.name()) }
  from.children().findAll { listOfNames.contains(it.name()) }.each{node ->
    //println "Removing: ${node.name()}"
    from.remove(node)
  }
}


def move(from, to, toName = null) {
  if (toName == null) {
    //just move it, no name change
    from.parent().remove(from)
    to.append(from)
  } else {
    to.appendNode(new QName(toName), from.attributes(), from.value())
	from.parent().remove(from)
  }
}
