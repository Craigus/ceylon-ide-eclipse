package com.redhat.ceylon.eclipse.imp.contentProposer;

import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LIDENTIFIER;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.MEMBER_OP;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.UIDENTIFIER;
import static com.redhat.ceylon.eclipse.imp.core.CeylonReferenceResolver.getDeclarationNode;
import static com.redhat.ceylon.eclipse.imp.editor.CeylonDocumentationProvider.getDocumentation;
import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Character.isLowerCase;
import static java.lang.Character.isUpperCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;
import org.eclipse.imp.editor.SourceProposal;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.imp.services.IContentProposer;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Generic;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.imp.parser.CeylonParseController;
import com.redhat.ceylon.eclipse.imp.tokenColorer.CeylonTokenColorer;
import com.redhat.ceylon.eclipse.imp.treeModelBuilder.CeylonLabelProvider;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;
import com.redhat.ceylon.eclipse.ui.ICeylonResources;

public class CeylonContentProposer implements IContentProposer {

    private static Image REFINEMENT = CeylonPlugin.getInstance()
            .getImageRegistry().get(ICeylonResources.CEYLON_REFINEMENT);
    
  /**
   * Returns an array of content proposals applicable relative to the AST of the given
   * parse controller at the given position.
   * 
   * (The provided ITextViewer is not used in the default implementation provided here
   * but but is stipulated by the IContentProposer interface for purposes such as accessing
   * the IDocument for which content proposals are sought.)
   * 
   * @param controller  A parse controller from which the AST of the document being edited
   *             can be obtained
   * @param int      The offset for which content proposals are sought
   * @param viewer    The viewer in which the document represented by the AST in the given
   *             parse controller is being displayed (may be null for some implementations)
   * @return        An array of completion proposals applicable relative to the AST of the given
   *             parse controller at the given position
   */
  public ICompletionProposal[] getContentProposals(IParseController controller,
      final int offset, ITextViewer viewer) {
    CeylonParseController cpc = (CeylonParseController) controller;
    
    //BEGIN HUGE BUG WORKAROUND
    //What is going on here is that when I have a list of proposals open
    //and then I type a character, IMP sends us the old syntax tree and
    //doesn't bother to even send us the character I just typed, except
    //in the ITextViewer. So we need to do some guessing to figure out
    //that there is a missing character in the token stream and take
    //corrective action. This should be fixed in IMP!
    String prefix="";
    int start=0;
    int end=0;
    CommonToken previousToken = null;
    for (CommonToken token: (List<CommonToken>) cpc.getTokenStream().getTokens()) {
    	if (/*t.getStartIndex()<=offset &&*/ token.getStopIndex()+1>=offset) {
			char charAtOffset = viewer.getDocument().get().charAt(offset-1);
			char charInTokenAtOffset = token.getText().charAt(offset-token.getStartIndex()-1);
			if (charAtOffset==charInTokenAtOffset) {
				if (isIdentifier(token)) {
	    			start = token.getStartIndex();
	    			end = token.getStopIndex();
	    			prefix = token.getText().substring(0, offset-token.getStartIndex());
	    		}
	    		else {
	    			prefix = "";
	    			start = offset;
	    			end = offset;
	    		}
			} 
			else {
				boolean isIdentifierChar = isJavaIdentifierPart(charAtOffset);
				if (previousToken!=null) {
					start = previousToken.getStartIndex();
					end = previousToken.getStopIndex();    				
					if (previousToken.getType()==MEMBER_OP && isIdentifierChar) {
						prefix = Character.toString(charAtOffset);
					}
					else if (isIdentifier(previousToken) && isIdentifierChar) {
						prefix = previousToken.getText()+charAtOffset;
					}
					else {
						prefix = isIdentifierChar ? 
								Character.toString(charAtOffset) : "";
					}
				}
				else {
					prefix = isIdentifierChar ? 
							Character.toString(charAtOffset) : "";
					start = offset;
					end = offset;
				}
			}
    		break;
    	}
    	previousToken = token;
    }
    //END BUG WORKAROUND
    
    if (cpc.getRootNode() != null) {
      Node node = cpc.getSourcePositionLocator()
    		  .findNode(cpc.getRootNode(), start, end);
      if (node==null) {
        node = cpc.getRootNode();
      }
            
      return constructCompletions(offset, prefix, 
    		  sortProposals(prefix, getProposals(node, prefix, cpc)),
    		  cpc, node);
    } 
    else {
      /*result.add(new ErrorProposal("No proposals available due to syntax errors", 
                 offset));*/
      return null;
    }
  }
  
  private static boolean isIdentifier(Token token) {
	  return token.getType()==LIDENTIFIER || 
			  token.getType()==UIDENTIFIER;
  }

  public ICompletionProposal[] constructCompletions(int offset, String prefix, 
        TreeMap<String, Declaration> map, CeylonParseController cpc, Node node) {
      List<ICompletionProposal> result = new ArrayList<ICompletionProposal>();
      for (String keyword: CeylonTokenColorer.keywords) {
          if (!prefix.isEmpty() && keyword.startsWith(prefix.toLowerCase())) {
              result.add(sourceProposal(offset, prefix, null, 
                      keyword + " keyword", keyword, keyword, 
                      false));
          }
      }
      for (final Declaration d: map.values()) {
        if (d instanceof TypeDeclaration || (d instanceof Method && d.isToplevel())) {
          result.add(sourceProposal(offset, prefix, 
                  CeylonLabelProvider.getImage(d),
                  getDocumentation(getDeclarationNode(cpc, d)), 
                  getDescriptionFor(d, false), 
        		  getTextFor(d, false), false));
        }
        if (d instanceof TypedDeclaration || d instanceof Class) {
          result.add(sourceProposal(offset, prefix, 
                  CeylonLabelProvider.getImage(d),
                  getDocumentation(getDeclarationNode(cpc, d)), 
                  getDescriptionFor(d, true), 
        		  getTextFor(d, true), true));
          if (node.getScope() instanceof ClassOrInterface &&
                  ((ClassOrInterface) node.getScope()).isInheritedFromSupertype(d)) {
              result.add(sourceProposal(offset, prefix, 
                      REFINEMENT, 
                      getDocumentation(getDeclarationNode(cpc, d)), 
                      getRefinementDescriptionFor(d) + " - refine declaration in " + 
                      ((Declaration) d.getContainer()).getName(), 
                      getRefinementTextFor(d), false));
          }
        }
      }
      return result.toArray(new ICompletionProposal[result.size()]);
  }

  public TreeMap<String, Declaration> sortProposals(final String prefix,
		Map<String, Declaration> proposals) {
	TreeMap<String, Declaration> map = new TreeMap<String, Declaration>(new Comparator<String>() {
        public int compare(String x, String y) {
        	int lowers = prefix.length()==0 || isLowerCase(prefix.charAt(0)) ? -1 : 1;
        	if (isLowerCase(x.charAt(0)) && 
        			isUpperCase(y.charAt(0))) {
        		return lowers;
        	}
        	else if (isUpperCase(x.charAt(0)) && 
        			isLowerCase(y.charAt(0))) {
        		return -lowers;
        	}
        	else {
        		return x.compareTo(y);
        	}
        }
      });
    map.putAll(proposals);
	return map;
  }

  private SourceProposal sourceProposal(final int offset, final String prefix,
		final Image image, String doc, String desc, final String text, 
		final boolean selectParams) {
	return new SourceProposal(desc, text, "", 
			                  new Region(offset - prefix.length(), prefix.length()), 
			                  offset + text.length(), doc) { 
      @Override
      public Image getImage() {
	    return image;
	  }
	  @Override
	  public Point getSelection(IDocument document) {
	      if (selectParams) {
    	      int loc = text.indexOf('(');
    	      int start;
    	      int length;
    	      if (loc<0||text.contains("()")) {
    	    	start = offset+text.length()-prefix.length();
    	    	length = 0;
    	      }
    	      else {
    		    start = offset-prefix.length()+loc+1;
    		    length = text.length()-loc-2;
    	      }
    	      return new Point(start, length);
          }
	      else {
	          return new Point(offset + text.length()-prefix.length(), 0);
	      }
	  }
	};
  }

  private Map<String, Declaration> getProposals(Node node, String prefix,
		  CeylonParseController cpc) {
    //TODO: substitute type arguments to receiving type
    if (node instanceof Tree.QualifiedMemberExpression) {
      ProducedType type = ((Tree.QualifiedMemberExpression) node).getPrimary().getTypeModel();
      if (type!=null) {
        return type.getDeclaration().getMatchingMemberDeclarations(prefix);
      }
      else {
        return Collections.emptyMap();
      }
    }
    else if (node instanceof Tree.QualifiedTypeExpression) {
      ProducedType type = ((Tree.QualifiedTypeExpression) node).getPrimary().getTypeModel();
      if (type!=null) {
        return type.getDeclaration().getMatchingMemberDeclarations(prefix);
      }
      else {
        return Collections.emptyMap();
      }
    }
    else {
      Map<String, Declaration> result = new TreeMap<String, Declaration>();
      Module languageModule = cpc.getContext().getModules().getLanguageModule();
      if (languageModule!=null) {
        for (Package languageScope: languageModule.getPackages() ) {
          result.putAll(languageScope.getMatchingDeclarations(null, prefix));
        }
      }
      result.putAll(node.getScope().getMatchingDeclarations(node.getUnit(), prefix));
      return result;
    }
  }

  public static String getTextFor(Declaration d, boolean includeArgs) {
    StringBuilder result = new StringBuilder(d.getName());
    if (d instanceof Generic) {
      List<TypeParameter> types = ((Generic) d).getTypeParameters();
      if (!types.isEmpty()) {
        result.append("<");
        for (TypeParameter p: types) {
          result.append(p.getName()).append(", ");
        }
        result.setLength(result.length()-2);
        result.append(">");
      }
    }
    if (includeArgs && d instanceof Functional) {
      List<ParameterList> plists = ((Functional) d).getParameterLists();
      if (plists!=null && !plists.isEmpty()) {
        ParameterList params = plists.get(0);
        if (params.getParameters().isEmpty()) {
          result.append("()");
        }
        else {
          result.append("(");
          for (Parameter p: params.getParameters()) {
            result.append(p.getName()).append(", ");
          }
          result.setLength(result.length()-2);
          result.append(")");
        }
      }
    }
    return result.toString();
  }
  
  public static String getRefinementTextFor(Declaration d) {
    return getDeclarationTextFor(d) + getDescriptionFor(d, true);
  }

  public static String getRefinementDescriptionFor(Declaration d) {
      return getDeclarationTextFor(d) + getDescriptionFor(d, true);
    }

  public static String getDeclarationTextFor(Declaration d) {
    StringBuilder result = new StringBuilder("shared actual ");
    if (d instanceof Class) {
        result.append("class");
    }
    else if (d instanceof TypedDeclaration) {
        TypedDeclaration td = (TypedDeclaration) d;
        String typeName = td.getType().getProducedTypeName();
        if (d instanceof Method) {
            if (typeName.equals("Void")) { //TODO: fix this!
                result.append("void");
            }
            else {
                result.append(typeName);
            }
        }
        else {
            result.append(typeName);
        }
    }
    return result.append(" ").toString();
}
  
  public static String getDescriptionFor(Declaration d, boolean includeArgs) {
      StringBuilder result = new StringBuilder(d.getName());
      if (d instanceof Generic) {
        List<TypeParameter> types = ((Generic) d).getTypeParameters();
        if (!types.isEmpty()) {
          result.append("<");
          for (TypeParameter p: types) {
            result.append(p.getName()).append(", ");
          }
          result.setLength(result.length()-2);
          result.append(">");
        }
      }
      if (includeArgs && d instanceof Functional) {
        List<ParameterList> plists = ((Functional) d).getParameterLists();
        if (plists!=null && !plists.isEmpty()) {
          ParameterList params = plists.get(0);
          if (params.getParameters().isEmpty()) {
            result.append("()");
          }
          else {
            result.append("(");
            for (Parameter p: params.getParameters()) {
              result.append(p.getType().getProducedTypeName()).append(" ")
                  .append(p.getName()).append(", ");
            }
            result.setLength(result.length()-2);
            result.append(")");
          }
        }
      }
      if (d.isToplevel()) {
        String pkg = d.getUnit().getPackage().getQualifiedNameString();
        if (pkg.isEmpty()) pkg="default package";
        result.append(" [").append(pkg).append("]");
      }
      return result.toString();
    }
    
  /*private String getPrefix(Node node, int offset) {
    if (node instanceof Tree.SimpleType) {
      Tree.Identifier id = ((Tree.SimpleType) node).getIdentifier();
      return getPrefix(offset, id);
    }
    else if (node instanceof Tree.StaticMemberOrTypeExpression) {
        Tree.Identifier id = ((Tree.StaticMemberOrTypeExpression) node).getIdentifier();
        return getPrefix(offset, id);
      }
    else if (node instanceof Tree.Declaration) {
      Tree.Identifier id = ((Tree.Declaration) node).getIdentifier();
      return getPrefix(offset, id);
    }
    else if (node instanceof Tree.NamedArgument) {
        Tree.Identifier id = ((Tree.NamedArgument) node).getIdentifier();
        return getPrefix(offset, id);
      }
    else {
      return "";
    }
  }

  private String getPrefix(int offset, Tree.Identifier id) {
    if (id==null||id.getText().equals("")) return "";
    if (offset<0) return id.getText(); 
    int index = offset-((CommonToken) id.getToken()).getStartIndex();
    if (index<=0) return "";
	return id.getText().substring(0, index);
  }*/
  
}