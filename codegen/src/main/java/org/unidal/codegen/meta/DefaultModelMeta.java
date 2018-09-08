package org.unidal.codegen.meta;

import java.io.Reader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.xml.parsers.SAXParserFactory;

import org.jdom.Document;
import org.jdom.Element;
import org.unidal.lookup.annotation.Named;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Named(type = ModelMeta.class)
public class DefaultModelMeta implements ModelMeta {
	private void addAttributes(Element element, Collection<AttributeEntry> attributes) {
		for (AttributeEntry e : attributes) {
			Element attribute = new Element("attribute");
			String name = e.getName();

			if (!name.contains(":")) {
				attribute.setAttribute("name", name);
				attribute.setAttribute("value-type", e.getValueType());

				if (e.getFormat() != null) {
					attribute.setAttribute("format", e.getFormat());
				}

				element.addContent(attribute);
			}
		}
	}

	private void addElementRefs(Element parent, Collection<ElementEntry> elementRefs, String parentName) {
		for (ElementEntry e : elementRefs) {
			Element elementRef = new Element("entity-ref");

			elementRef.setAttribute("name", e.getName());

			if (e.isList() || e.isList(parentName)) {
				elementRef.setAttribute("type", "list");

				if (e.getListName() == null) {
					String listName = guessListName(e);

					if (listName != null) {
						elementRef.setAttribute("names", listName);
					}
				} else {
					elementRef.setAttribute("names", e.getListName());
					elementRef.setAttribute("xml-indent", "true");
				}
			}

			parent.addContent(elementRef);
		}
	}

	private void addElements(Element parent, Collection<ElementEntry> elements, String parentName) {
		for (ElementEntry e : elements) {
			Element element = new Element("element");

			element.setAttribute("name", e.getName());
			element.setAttribute("value-type", e.getValueType());

			if (e.getFormat() != null) {
				element.setAttribute("format", e.getFormat());
			}

			if (e.isList() || e.isList(parentName)) {
				element.setAttribute("type", "list");

				if (e.getListName() == null) {
					String listName = guessListName(e);

					if (listName != null) {
						element.setAttribute("names", listName);
					}
				} else {
					element.setAttribute("names", e.getListName());
					element.setAttribute("xml-indent", "true");
				}

				if (e.getAlias() != null) {
					element.setAttribute("alias", e.getAlias());
				}
			}

			if (e.isValue()) {
				element.setAttribute("text", "true");
			}

			parent.addContent(element);
		}
	}

	@Override
	public Document getCodegen(Reader reader) {
		XmlMetaParser parser = new XmlMetaParser();

		parser.parse(new InputSource(reader));
		parser.optimize();

		Element codegen = toElement(parser.getEntries());

		return new Document(codegen);
	}

	@Override
	public Document getManifest(String codegenXml, String modelXml) {
		Element manifest = new Element("manifest");

		manifest.addContent(new Element("file").setAttribute("path", codegenXml));
		manifest.addContent(new Element("file").setAttribute("path", modelXml));

		return new Document(manifest);
	}

	@Override
	public Document getModel(String packageName) {
		Element model = new Element("model");

		model.setAttribute("model-package", packageName);
		model.setAttribute("enable-sax-parser", "true");
		model.setAttribute("enable-base-visitor", "true");

		return new Document(model);
	}

	private String guessListName(ElementEntry e) {
		String name = e.getName();
		int len = name.length();
		char last = name.charAt(len - 1);

		switch (last) {
		case 's':
			return name + "es";
		case 'y':
			return name.substring(0, len - 1) + "ies";
		default:
			return name + 's';
		}
	}

	private Element toElement(List<ElementEntry> entries) {
		Element model = new Element("model");

		for (ElementEntry entry : entries) {
			String name = entry.getName();

			Element element = new Element("entity");

			element.setAttribute("name", name);

			if (entry.isRoot()) {
				element.setAttribute("root", "true");
			}

			addAttributes(element, entry.getAttributes());
			addElements(element, entry.getElements(), name);
			addElementRefs(element, entry.getElementRefs(), name);
			model.addContent(element);
		}

		return model;
	}

	static final class AttributeEntry {
		private String m_name;

		private Set<String> m_formats = new HashSet<String>();

		private Set<String> m_valueTypes = new HashSet<String>();

		public String getFormat() {
			if (m_formats.size() == 0 || m_valueTypes.size() > 1) {
				return null;
			} else {
				StringBuilder sb = new StringBuilder(64);
				boolean first = true;

				for (String format : m_formats) {
					if (!first) {
						sb.append('|');
					}

					first = false;
					sb.append(format);
				}

				return sb.toString();
			}
		}

		public String getName() {
			return m_name;
		}

		public String getValueType() {
			return Utils.getValueType(m_valueTypes);
		}

		public void setFormat(String format) {
			if (format != null && !m_formats.contains(format)) {
				m_formats.add(format);
			}
		}

		public void setName(String name) {
			m_name = name;
		}

		public void setValueType(String valueType) {
			if (valueType != null && !m_valueTypes.contains(valueType)) {
				m_valueTypes.add(valueType);
			}
		}

		@Override
		public String toString() {
			return String.format("AttributeEntry[name: %s, types: %s, format: %s]", m_name, m_valueTypes, m_formats);
		}
	}

	static final class ElementEntry {
		private String m_name;

		private Set<String> m_formats = new HashSet<String>();

		private Set<String> m_valueTypes = new HashSet<String>();

		private boolean m_list;

		private String m_listName;

		private boolean m_value;

		private boolean m_root;

		private String m_alias;

		private Map<String, Occurance> m_occurs = new HashMap<String, Occurance>();

		private Map<String, AttributeEntry> m_attributes = new LinkedHashMap<String, AttributeEntry>();

		private Map<String, ElementEntry> m_elements = new LinkedHashMap<String, ElementEntry>();

		public void addAttribute(AttributeEntry attribute) {
			AttributeEntry a = m_attributes.get(attribute.getName());

			if (a == null) {
				a = attribute;
				m_attributes.put(a.getName(), a);
			}

			a.setValueType(attribute.getValueType());
			a.setFormat(attribute.getFormat());
		}

		public void addElement(ElementEntry element) {
			ElementEntry e = m_elements.get(element.getName());

			if (e == null) {
				e = element;
				m_elements.put(e.getName(), e);
			}

			for (String valueType : element.getValueTypes()) {
				e.addValueType(valueType);
			}

			e.setFormat(element.getFormat());
			e.setValue(element.isValue());
		}

		public void addValueType(String valueType) {
			if (valueType != null && !m_valueTypes.contains(valueType)) {
				m_valueTypes.add(valueType);
			}
		}

		public String getAlias() {
			return m_alias;
		}

		public Collection<AttributeEntry> getAttributes() {
			return m_attributes.values();
		}

		public Collection<ElementEntry> getElementRefs() {
			List<ElementEntry> elementRefs = new ArrayList<ElementEntry>();

			for (ElementEntry e : m_elements.values()) {
				if (!e.isSimpleElement()) {
					elementRefs.add(e);
				}
			}

			return elementRefs;
		}

		public Collection<ElementEntry> getElements() {
			List<ElementEntry> elements = new ArrayList<ElementEntry>();

			for (ElementEntry e : m_elements.values()) {
				if (e.isSimpleElement()) {
					elements.add(e);
				}
			}

			return elements;
		}

		public String getFormat() {
			if (m_formats.size() == 0 || m_valueTypes.size() > 1) {
				return null;
			} else {
				StringBuilder sb = new StringBuilder(64);
				boolean first = true;

				for (String format : m_formats) {
					if (!first) {
						sb.append('|');
					}

					first = false;
					sb.append(format);
				}

				return sb.toString();
			}

		}

		public String getListName() {
			return m_listName;
		}

		public String getName() {
			return m_name;
		}

		public String getValueType() {
			return Utils.getValueType(m_valueTypes);
		}

		Set<String> getValueTypes() {
			return m_valueTypes;
		}

		public void increaseOccurs(String parentName) {
			Occurance occur = m_occurs.get(parentName);

			if (occur == null) {
				occur = new Occurance();
				m_occurs.put(parentName, occur);
			}

			occur.increaseOccurs();
		}

		public boolean isList() {
			return m_list;
		}

		public boolean isList(String parentName) {
			Occurance occur = m_occurs.get(parentName);
			return occur != null && occur.getMaxOccurs() > 1;
		}

		public boolean isRoot() {
			return m_root;
		}

		public boolean isSimpleElement() {
			return m_attributes.isEmpty() && m_elements.isEmpty();
		}

		public boolean isValue() {
			return m_value;
		}

		public void removeElement(ElementEntry element) {
			m_elements.remove(element.getName());
		}

		public void resetOccurs(String parentName) {
			Occurance occur = m_occurs.get(parentName);

			if (occur != null) {
				occur.resetOccurs();
			}
		}

		public void setAlias(String alias) {
			m_alias = alias;
		}

		public void setFormat(String format) {
			if (format != null && !m_formats.contains(format)) {
				m_formats.add(format);
			}
		}

		public void setList(boolean list) {
			m_list = list;
		}

		public void setListName(String listName) {
			m_listName = listName;
		}

		public void setName(String name) {
			m_name = name;
		}

		public void setRoot(boolean root) {
			m_root = root;
		}

		public void setValue(boolean value) {
			m_value |= value;
		}

		@Override
		public String toString() {
			return String.format("ElementEntry[name: %s, attributes: %s, list: %s, elements: %s]", m_name,
			      m_attributes.keySet(), m_list ? "" : m_listName, m_elements.keySet());
		}
	}

	static final class Occurance {
		private int m_maxOccurs;

		private int m_occurs;

		public int getMaxOccurs() {
			return m_maxOccurs;
		}

		public void increaseOccurs() {
			m_occurs++;

			if (m_occurs > m_maxOccurs) {
				m_maxOccurs = m_occurs;
			}
		}

		public void resetOccurs() {
			m_occurs = 0;
		}

		@Override
		public String toString() {
			return String.format("%s[%s, %s]", getClass().getSimpleName(), m_occurs, m_maxOccurs);
		}
	}

	static final class Utils {
		private static SimpleDateFormat[] s_dateFormats = new SimpleDateFormat[] {
			   new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH),
		      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
		      new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
		      new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH),
		      new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH),
		      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH), 
		      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH), 
		      };

		private static Map<String, String> s_mapping = new HashMap<String, String>() {
			private static final long serialVersionUID = 1L;
			{
				put("int:long", "long");
				put("double:int", "double");
				put("double:long", "double");
			}
		};

		public static String getValueType(Set<String> valueTypes) {
			List<String> types = new ArrayList<String>(valueTypes);
			String current = null;

			Collections.sort(types);

			for (String type : types) {
				if (current == null) {
					current = type;
				} else {
					String resolved = s_mapping.get(current + ":" + type);

					if (resolved != null) {
						current = resolved;
					} else {
						return "String";
					}
				}
			}

			if (current != null) {
				return current;
			} else {
				return "String";
			}
		}

		public static String[] guessValueTypeAndFormat(String value) {
			String[] typeAndFormat = new String[2];
			String type = null;
			String format = null;

			if (value.equals("true") || value.equals("false")) {
				type = "boolean";
			} else {
				if (value.indexOf('.') >= 0) {
					try {
						Double.parseDouble(value);
						type = "double";
					} catch (Exception e) {
						// ignore it
					}
				}

				if (type == null) {
					try {
						Integer.parseInt(value);
						type = "int";
					} catch (Exception e) {
						// ignore it
					}
				}

				if (type == null) {
					try {
						Long.parseLong(value);
						type = "long";
					} catch (Exception e) {
						// ignore it
					}
				}

				if (type == null) {
					// guess date
					for (SimpleDateFormat dateFormat : s_dateFormats) {
						try {
							dateFormat.parse(value);

							type = "Date";
							format = dateFormat.toPattern();
							break;
						} catch (ParseException e) {
							// ignore it
						}
					}
				}
			}

			typeAndFormat[0] = (type == null ? "String" : type);
			typeAndFormat[1] = format;
			return typeAndFormat;
		}
	}

	static final class XmlMetaParser extends DefaultHandler {
		private Stack<ElementEntry> m_stack = new Stack<ElementEntry>();

		private Map<String, ElementEntry> m_elementMap = new LinkedHashMap<String, ElementEntry>();

		public XmlMetaParser() {
			m_stack.push(new ElementEntry());
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			String str = new String(ch, start, length).trim();

			if (str.length() == 0) {
				return;
			}

			ElementEntry element = m_stack.peek();
			Collection<AttributeEntry> attributes = element.getAttributes();
			String[] typeAndFormat = Utils.guessValueTypeAndFormat(str);

			if (!attributes.isEmpty()) {
				ElementEntry text = new ElementEntry();

				text.setName("text");
				text.setValue(true);
				text.addValueType(typeAndFormat[0]);
				text.setFormat(typeAndFormat[1]);
				element.addElement(text);
			} else if (!element.getElements().isEmpty() || !element.getElementRefs().isEmpty()) {
				throw new RuntimeException("Text are not supported to entity with elements and element-refs.");
			} else {
				element.addValueType(typeAndFormat[0]);
				element.setFormat(typeAndFormat[1]);
			}
		}

		@Override
		public void endElement(String uri, String localName, String name) throws SAXException {
			ElementEntry entry = m_stack.pop();

			for (ElementEntry e : entry.getElements()) {
				e.resetOccurs(entry.getName());
			}

			for (ElementEntry e : entry.getElementRefs()) {
				e.resetOccurs(entry.getName());
			}
		}

		public List<ElementEntry> getEntries() {
			List<ElementEntry> entries = new ArrayList<ElementEntry>();

			for (ElementEntry entry : m_elementMap.values()) {
				if (!entry.isSimpleElement()) {
					entries.add(entry);
				}
			}

			return entries;
		}

		public void optimize() {
			Set<ElementEntry> done = new HashSet<ElementEntry>();
			Set<String> trash = new HashSet<String>();

			optimize(m_stack.peek(), done, trash);

			for (String name : trash) {
				m_elementMap.remove(name);
			}
		}

		private void optimize(ElementEntry entry, Set<ElementEntry> done, Set<String> trash) {
			if (done.contains(entry)) {
				return;
			}

			done.add(entry);

			Map<String, List<ElementEntry>> map = new HashMap<String, List<ElementEntry>>();

			for (ElementEntry ref : entry.getElementRefs()) {
				String name = ref.getName();
				ElementEntry e = m_elementMap.get(name);

				if (done.size() != 1) { // skip root
					if (e.getAttributes().isEmpty()) { // no attribute
						Collection<ElementEntry> elements = e.getElements();
						Collection<ElementEntry> elementRefs = e.getElementRefs();

						if (elements.isEmpty() && elementRefs.size() == 1) {
							// list of EntityRef
							ElementEntry first = elementRefs.iterator().next();

							if (first.isList(name)) { // occurs more than once
								ref.setList(true);
								ref.setName(first.getName());
								ref.setListName(name);
								trash.add(name);
							}
						} else if (elements.size() == 1 && elementRefs.isEmpty()) {
							// list of Element
							ElementEntry first = elements.iterator().next();

							if (first.isList(name)) {
								ref.setList(true);
								ref.setName(first.getName());
								ref.setListName(name);
								trash.add(name);
								ref.removeElement(first);
								e.removeElement(first);
							}
						}
					}
				}

				optimize(e, done, trash);

				// check potential conflicting name
				List<ElementEntry> list = map.get(ref.getName());
				if (list == null) {
					list = new ArrayList<ElementEntry>();
					map.put(ref.getName(), list);
				}
				list.add(ref);
			}

			for (List<ElementEntry> list : map.values()) {
				if (list.size() > 1) {
					for (ElementEntry item : list) {
						item.setAlias(item.getListName() + "-element");
					}
				}
			}
		}

		public void parse(InputSource source) {
			try {
				SAXParserFactory factory = SAXParserFactory.newInstance();

				factory.setNamespaceAware(true);
				factory.newSAXParser().parse(source, this);
			} catch (Exception e) {
				throw new RuntimeException("Error when parsing XML data. " + e.getMessage(), e);
			}
		}

		@Override
		public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
			String elementName = localName;
			ElementEntry parent = m_stack.peek();
			ElementEntry element = m_elementMap.get(elementName);

			if (element == null) {
				element = new ElementEntry();
				element.setName(elementName);
				m_elementMap.put(elementName, element);
			}

			int len = attributes.getLength();

			for (int i = 0; i < len; i++) {
				AttributeEntry attribute = new AttributeEntry();
				String[] typeAndFormat = Utils.guessValueTypeAndFormat(attributes.getValue(i));

				attribute.setName(attributes.getQName(i));
				attribute.setValueType(typeAndFormat[0]);
				attribute.setFormat(typeAndFormat[1]);
				element.addAttribute(attribute);
			}

			if (parent.getName() == null) {
				element.setRoot(true);
			}

			element.increaseOccurs(parent.getName());
			parent.addElement(element);
			m_stack.push(element);
		}
	}
}
