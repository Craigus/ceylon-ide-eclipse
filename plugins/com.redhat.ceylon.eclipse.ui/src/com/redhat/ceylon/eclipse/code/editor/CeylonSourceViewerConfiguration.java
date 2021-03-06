package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.findNode;
import static com.redhat.ceylon.eclipse.code.resolve.CeylonReferenceResolver.getReferencedDeclaration;
import static org.eclipse.jdt.ui.PreferenceConstants.APPEARANCE_JAVADOC_FONT;
import static org.eclipse.jface.dialogs.DialogSettings.getOrCreateSection;
import static org.eclipse.jface.text.AbstractInformationControlManager.ANCHOR_GLOBAL;
import static org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.information.IInformationPresenter;
import org.eclipse.jface.text.information.IInformationProvider;
import org.eclipse.jface.text.information.IInformationProviderExtension;
import org.eclipse.jface.text.information.InformationPresenter;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.hover.BestMatchHover;
import com.redhat.ceylon.eclipse.code.hover.BrowserInformationControl;
import com.redhat.ceylon.eclipse.code.hover.CeylonAnnotationHover;
import com.redhat.ceylon.eclipse.code.hover.DocHover;
import com.redhat.ceylon.eclipse.code.outline.CeylonHierarchyContentProvider;
import com.redhat.ceylon.eclipse.code.outline.CeylonOutlineBuilder;
import com.redhat.ceylon.eclipse.code.outline.HierarchyPopup;
import com.redhat.ceylon.eclipse.code.outline.OutlinePopup;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.code.propose.CompletionProcessor;
import com.redhat.ceylon.eclipse.code.quickfix.CeylonQuickFixController;
import com.redhat.ceylon.eclipse.code.resolve.CeylonHyperlinkDetector;
import com.redhat.ceylon.eclipse.code.resolve.JavaHyperlinkDetector;
import com.redhat.ceylon.eclipse.code.search.FindContainerVisitor;
import com.redhat.ceylon.eclipse.ui.CeylonPlugin;

public class CeylonSourceViewerConfiguration extends TextSourceViewerConfiguration {
	
    protected final CeylonEditor editor;
    private final CompletionProcessor processor;

    public CeylonSourceViewerConfiguration(IPreferenceStore prefStore, 
    		CeylonEditor editor) {
        super(prefStore);
        this.editor = editor;
        processor = new CompletionProcessor(editor);
    }
    
    public PresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
        // BUG Perhaps we shouldn't use a PresentationReconciler; its JavaDoc says it runs in the UI thread!
        PresentationReconciler reconciler= new PresentationReconciler();
        PresentationDamageRepairer damageRepairer = new PresentationDamageRepairer(sourceViewer);
        reconciler.setRepairer(damageRepairer, DEFAULT_CONTENT_TYPE);
		reconciler.setDamager(damageRepairer, DEFAULT_CONTENT_TYPE);
        return reconciler;
    }

    private final class Warmup implements IRunnableWithProgress {
		@Override
		public void run(IProgressMonitor monitor) throws InvocationTargetException,
				InterruptedException {
			
			monitor.beginTask("Warming up completion processor", 100000);
			
	        List<Package> packages = editor.getParseController()
	        		.getRootNode().getUnit().getPackage()
	        		.getModule().getAllPackages();
	        
			monitor.worked(10000);
			
			for (Package p: packages) {
				p.getMembers();
				monitor.worked(90000/packages.size());
				if (monitor.isCanceled()) return;
			}

			monitor.done();
		}
	}
    public ContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
    	
        ContentAssistant ca= new ContentAssistant() {
        	protected void install() {
                setInformationControlCreator(new DocHover(editor)
                        .getHoverControlCreator("Click for focus"));
        		super.install();
        	}
        };
        ca.addCompletionListener(new ICompletionListener() {
			@Override
			public void selectionChanged(ICompletionProposal proposal,
					boolean smartToggle) {}
			@Override
			public void assistSessionStarted(ContentAssistEvent event) {
				editor.pauseBackgroundParsing();
				try {
					editor.getSite().getWorkbenchWindow().run(true, true, new Warmup());
				} 
				catch (Exception e) {}

			}			
			@Override
			public void assistSessionEnded(ContentAssistEvent event) {
				editor.unpauseBackgroundParsing();
				editor.scheduleParsing();
			}
		});
		ca.setContentAssistProcessor(processor, DEFAULT_CONTENT_TYPE);
        ca.enableAutoInsert(true);
        ca.enableAutoActivation(true);
        ca.setAutoActivationDelay(500);
        ca.enableColoredLabels(true);
        //ca.enablePrefixCompletion(true); //TODO: prefix completion stuff in ICompletionProposalExtension3
        return ca;
    }

    @Override
    public IQuickAssistAssistant getQuickAssistAssistant(ISourceViewer sourceViewer) {
        CeylonQuickFixController quickAssist = new CeylonQuickFixController(editor);
        quickAssist.enableColoredLabels(true);
		return quickAssist;
    }

    public CeylonAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
        return new CeylonAnnotationHover();
    }

    public CeylonAnnotationHover getOverviewRulerAnnotationHover(ISourceViewer sourceViewer) {
        return new CeylonAnnotationHover();
    }

    public IAutoEditStrategy[] getAutoEditStrategies(ISourceViewer sourceViewer, String contentType) {
        return new IAutoEditStrategy[] { new CeylonAutoEditStrategy() };
    }
        
    public ITextDoubleClickStrategy getDoubleClickStrategy(ISourceViewer sourceViewer, String contentType) {
        return new DoubleClickStrategy(); 
    }

    public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
    	CeylonParseController pc = getParseController();
		return new IHyperlinkDetector[] { new CeylonHyperlinkDetector(pc), 
    			new JavaHyperlinkDetector(pc) };
    }

	protected CeylonParseController getParseController() {
		return editor.getParseController();
	}

    /**
     * Used to present hover help (anything else?)
     */
    public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
        return new IInformationControlCreator() {
            public IInformationControl createInformationControl(Shell parent) {
                return new BrowserInformationControl(parent, 
                		APPEARANCE_JAVADOC_FONT, (String)null, null
                		/*CeylonTokenColorer.getCurrentThemeColor("docHover")*/) {
					@Override
					public Point computeSizeHint() {
						Point sh = super.computeSizeHint();
						return new Point(sh.x+20, sh.y*3/2);
					}
                };
            }
        };
    }

    public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
        return new BestMatchHover(editor);
    }

    /*public IInformationPresenter getInformationPresenter(ISourceViewer sourceViewer) {
        if (infoPresenter == null) {
            infoPresenter= new InformationPresenter(getInformationControlCreator(sourceViewer));
            infoPresenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
            infoPresenter.setAnchor(ANCHOR_GLOBAL);

            IInformationProvider provider= new HoverInformationProvider();
            infoPresenter.setInformationProvider(provider, IDocument.DEFAULT_CONTENT_TYPE);
            //infoPresenter.setSizeConstraints(500, 100, true, false);
            //infoPresenter.setRestoreInformationControlBounds(getSettings("outline_presenter_bounds"), true, true); //$NON-NLS-1$
        }
        return infoPresenter;
    }

    private final class HoverInformationProvider implements IInformationProvider {
		private IAnnotationModel annotationModel= editor.getDocumentProvider()
				.getAnnotationModel(editor.getEditorInput());

		private List<Annotation> getParserAnnotationsAtOffset(int offset) {
		    List<Annotation> result= new LinkedList<Annotation>();
		    if (annotationModel != null) {
		        for(Iterator<Annotation> iter= annotationModel.getAnnotationIterator(); 
		        		iter.hasNext(); ) {
		            Annotation ann= iter.next();
		            if (annotationModel.getPosition(ann).includes(offset) && 
		            		isParseAnnotation(ann)) {
		                result.add(ann);
		            }
		        }
		    }
		    return result;
		}

		public IRegion getSubject(ITextViewer textViewer, int offset) {
			List<Annotation> parserAnnsAtOffset = getParserAnnotationsAtOffset(offset);
			if (!parserAnnsAtOffset.isEmpty()) {
				Annotation ann= parserAnnsAtOffset.get(0);
				Position pos= annotationModel.getPosition(ann);
				return new Region(pos.offset, pos.length);
			}
			Node selNode= findNode(editor.getParseController().getRootNode(), offset);
		    return new Region(getStartOffset(selNode), getLength(selNode));
		}

		public String getInformation(ITextViewer textViewer, IRegion subject) {
			List<Annotation> parserAnnsAtOffset = getParserAnnotationsAtOffset(subject.getOffset());
			if (!parserAnnsAtOffset.isEmpty()) {
				return parserAnnsAtOffset.get(0).getText();
			}

			CeylonParseController pc = editor.getParseController();
			Node selNode= findNode(pc.getRootNode(), subject.getOffset());
		    return new CeylonDocumentationProvider().getDocumentation(selNode, pc);
			return null;
		}
	}*/

	private IDialogSettings getSettings() { return CeylonPlugin.getInstance().getDialogSettings(); }
    
    public IInformationPresenter getCodePresenter(ISourceViewer sourceViewer) {
        InformationPresenter presenter = new InformationPresenter(new IInformationControlCreator() {
			@Override
			public IInformationControl createInformationControl(Shell parent) {
				final CodePopup pop = new CodePopup(parent, SWT.RESIZE, editor);
				pop.viewer.configure(new CeylonSourceViewerConfiguration(editor.getPrefStore(), editor) {
					@Override
					protected CeylonParseController getParseController() {
						return pop.getParseController();
					}
				});
				return pop;
			}
		});
        presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        presenter.setAnchor(ANCHOR_GLOBAL);
        presenter.setInformationProvider(new OutlineInformationProvider(), DEFAULT_CONTENT_TYPE); //TODO!!
        presenter.setSizeConstraints(40, 10, true, false);
		presenter.setRestoreInformationControlBounds(getOrCreateSection(getSettings(),"code_presenter_bounds"), true, true);
        return presenter;
    }
    
	private class OutlineInformationProvider 
            implements IInformationProvider, IInformationProviderExtension {
		@Override
    	public IRegion getSubject(ITextViewer textViewer, int offset) {
    		return new Region(offset, 0); // Could be anything, since it's ignored below in getInformation2()...
    	}
		@Override
    	public String getInformation(ITextViewer textViewer, IRegion subject) {
    		// shouldn't be called, given IInformationProviderExtension???
    		throw new UnsupportedOperationException();
    	}
		@Override
    	public Object getInformation2(ITextViewer textViewer, IRegion subject) {
    		return new CeylonOutlineBuilder().buildTree(editor.getParseController().getRootNode());
    	}
    }
	
    public IInformationPresenter getOutlinePresenter(ISourceViewer sourceViewer) {
        InformationPresenter presenter = new InformationPresenter(new IInformationControlCreator() {
			@Override
			public IInformationControl createInformationControl(Shell parent) {
				return new OutlinePopup(editor, parent, 
						SWT.RESIZE, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
			}
		});
        presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        presenter.setAnchor(ANCHOR_GLOBAL);
        presenter.setInformationProvider(new OutlineInformationProvider(), DEFAULT_CONTENT_TYPE);
        presenter.setSizeConstraints(40, 10, true, false);
		presenter.setRestoreInformationControlBounds(getOrCreateSection(getSettings(),"outline_presenter_bounds"), true, true);
        return presenter;
    }
    
    private class HierarchyInformationProvider 
            implements IInformationProvider, IInformationProviderExtension {
		@Override
    	public IRegion getSubject(ITextViewer textViewer, int offset) {
    		return new Region(offset, 0); // Could be anything, since it's ignored below in getInformation2()...
    	}
		@Override
    	public String getInformation(ITextViewer textViewer, IRegion subject) {
    		// shouldn't be called, given IInformationProviderExtension???
    		throw new UnsupportedOperationException();
    	}
		@Override
    	public Object getInformation2(ITextViewer textViewer, IRegion subject) {
    		Node selectedNode = getSelectedNode(editor);
    		Declaration declaration = getReferencedDeclaration(selectedNode);
    		if (declaration==null) {
    			FindContainerVisitor fcv = new FindContainerVisitor(selectedNode);
    			fcv.visit(editor.getParseController().getRootNode());
    			Tree.StatementOrArgument node = fcv.getStatementOrArgument();
    			if (node instanceof Tree.Declaration) {
    				declaration = ((Tree.Declaration) node).getDeclarationModel();
    			}
    		}
    		return new CeylonHierarchyContentProvider.RootNode(declaration);
    	}
    	//TODO: this is a copy/paste from AbstractFindAction
    	private Node getSelectedNode(CeylonEditor editor) {
    		CeylonParseController cpc = editor.getParseController();
    		return cpc.getRootNode()==null ? null : 
    			findNode(cpc.getRootNode(), 
    					(ITextSelection) editor.getSelectionProvider().getSelection());
    	}
    }

    public IInformationPresenter getHierarchyPresenter(ISourceViewer sourceViewer, boolean b) {
        InformationPresenter presenter = new InformationPresenter(new IInformationControlCreator() {
			@Override
			public IInformationControl createInformationControl(Shell parent) {
				return new HierarchyPopup(editor, parent, 
						SWT.RESIZE, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
			}
		});
        presenter.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
        presenter.setAnchor(ANCHOR_GLOBAL);
        presenter.setInformationProvider(new HierarchyInformationProvider(), DEFAULT_CONTENT_TYPE);
        presenter.setSizeConstraints(40, 10, true, false);
        presenter.setRestoreInformationControlBounds(getOrCreateSection(getSettings(),"hierarchy_presenter_bounds"), true, true);
        return presenter;
    }

}
