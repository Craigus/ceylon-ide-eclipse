package com.redhat.ceylon.eclipse.code.open;

import static com.redhat.ceylon.eclipse.code.editor.Util.getCurrentEditor;
import static com.redhat.ceylon.eclipse.code.editor.Util.getSelection;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.findNode;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.gotoLocation;
import static com.redhat.ceylon.eclipse.code.resolve.CeylonReferenceResolver.getReferencedNode;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator;

public class OpenSelectedDeclarationHandler extends AbstractHandler {
    
    private Tree.Declaration getSelectionTarget(ITextSelection textSel) {
        CeylonEditor editor = (CeylonEditor) getCurrentEditor();
        CeylonParseController pc= editor.getParseController();
        Tree.CompilationUnit ast= pc.getRootNode();
        if (ast == null) {
            return null;
        }
        else {
            Object sourceNode= findNode(ast, textSel.getOffset());
            if (sourceNode == null) {
                return null;
            }
            else {
                return getReferencedNode(sourceNode, pc);
            }
        }
    }

    private void go(Tree.Declaration dec) {
        CeylonEditor editor = (CeylonEditor) getCurrentEditor();
        CeylonSourcePositionLocator locator= editor.getParseController().getSourcePositionLocator();
        if (dec != null) {
            gotoLocation(locator.getPath(dec), locator.getStartOffset(dec));
        }
    }
    
    public boolean isEnabled() {
        IEditorPart editor = getCurrentEditor();
        return super.isEnabled() && editor instanceof CeylonEditor &&
                getSelectionTarget(getSelection((ITextEditor) editor))!=null;
    }
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IEditorPart editor = getCurrentEditor();
        if (editor instanceof CeylonEditor) {
            go(getSelectionTarget(getSelection((ITextEditor) editor)));
        }
        return null;
    }
        
}