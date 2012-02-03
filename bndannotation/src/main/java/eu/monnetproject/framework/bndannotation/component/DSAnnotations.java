package eu.monnetproject.framework.bndannotation.component;

import java.util.*;

import aQute.bnd.service.*;
import aQute.lib.osgi.*;
import aQute.libg.header.*;

/**
 * Analyze the class space for any classes that have an OSGi annotation for DS.
 * 
 */
public class DSAnnotations implements AnalyzerPlugin {

	public boolean analyzeJar(Analyzer analyzer) throws Exception {
		Map<String, Map<String, String>> header = OSGiHeader.parseHeader(analyzer
				.getProperty("-dsannotations"));
		if ( header.size()==0)
			return false;
		
		Set<Instruction> instructions = Instruction.replaceWithInstruction(header).keySet();
		Set<Clazz> list = new HashSet<Clazz>(analyzer.getClassspace().values());
		String sc = analyzer.getProperty(Constants.SERVICE_COMPONENT);
		List<String> names = new ArrayList<String>();
		if ( sc != null && sc.trim().length() > 0)
			names.add(sc);
		
		for (Iterator<Clazz> i = list.iterator(); i.hasNext();) {
			for (Instruction instruction : instructions) {
				Clazz c = i.next();
				System.out.println("fqn " + c.getFQN() + " " + instruction);
				if (instruction.matches(c.getFQN())) {
					if (instruction.isNegated())
						i.remove();
					else {
						ComponentDef definition = AnnotationReader.getDefinition(c, analyzer);
						if (definition != null) {
							definition.prepare(analyzer);
							String name = "OSGI-INF/" + definition.name + ".xml";
							names.add(name);
							analyzer.getJar().putResource(name,
									new TagResource(definition.getTag()));
						}
					}
				}
			}
		}
		sc = Processor.append(names.toArray(new String[names.size()]));
		analyzer.setProperty(Constants.SERVICE_COMPONENT, sc);
		return false;
	}
}
