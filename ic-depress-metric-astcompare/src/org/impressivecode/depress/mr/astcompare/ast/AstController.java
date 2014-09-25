package org.impressivecode.depress.mr.astcompare.ast;

import static org.impressivecode.depress.mr.astcompare.utils.Utils.saveStreamAsFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.team.core.history.IFileRevision;
import org.impressivecode.depress.mr.astcompare.db.DbHandler;
import org.impressivecode.depress.mr.astcompare.scm.ScmHandler;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;

/*
 ImpressiveCode Depress Framework
 Copyright (C) 2013  ImpressiveCode contributors

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
public class AstController {

    private final ExecutionContext exec;
    private FileDistiller distiller;
    private DbHandler db;
    private File previous;
    private File actual;
    private ScmHandler scmHandler;

    public AstController(final ExecutionContext exec, DbHandler db, ScmHandler scmHandler) {
        this.distiller = ChangeDistiller.createFileDistiller(Language.JAVA);
        this.db = db;
        this.exec = exec;
        this.scmHandler = scmHandler;
    }

    public void collectDataAndSaveInDb(IPackageFragment[] packages, String selectedProjectName, long revisionDateMin,
            long revisionDateMax) throws CanceledExecutionException, CoreException, IOException, SQLException {
        collectDataAndSaveInDb(packages, selectedProjectName, revisionDateMin, revisionDateMax, false);
    }

    public void collectDataAndSaveInDb(IPackageFragment[] packages, String selectedProjectName, long revisionDateMin,
            long revisionDateMax, boolean excludeTests) throws CanceledExecutionException, CoreException, IOException, SQLException {
        double progressIndex = 1.0d;
        previous = File.createTempFile("astFileA", ".java");
        actual = File.createTempFile("astFileB", ".java");

        for (IPackageFragment mypackage : packages) {
            checkIfCancelledAndSetProgress((progressIndex++ / packages.length) * 0.6d + 0.2d);

            if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {

                for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
                    checkIfCancelledAndSetProgress(null);

                    if (excludeTests) {
                        // check if should be excluded (is test class)
                        IResource resource = unit.getUnderlyingResource();
                        if (resource==null || resource.getType()!=IResource.FILE) {
                            continue;
                        } else {
                            IFile file = (IFile) resource;
                            String path = file.getRawLocation().toString();
                            if (path.toLowerCase().contains("tests\\src\\java") || path.toLowerCase().contains("tests/src/java")) {
                                continue;
                            }
                        }
                    }

                    IFileRevision[] revisions = scmHandler.getProperFileRevisions(unit.getResource());

                    if (revisions != null && revisions.length > 1) {

                        InputStream previousStream = null;

                        for (IFileRevision revision : revisions) {
                            checkIfCancelledAndSetProgress(null);

                            if (previousStream == null) {
                                try {
                                    previousStream = revision.getStorage(null).getContents();
                                    
                                    saveStreamAsFile(previousStream, previous);
                                } catch (CoreException e) {
                                    // skip
                                }

                                continue;
                            } else {
                                try {
                                    saveStreamAsFile(revision.getStorage(null).getContents(), actual);
    
                                    compareAstAndSaveToDb(selectedProjectName, revisionDateMin, revisionDateMax, revision);
    
                                    swapActualAndPreviousFile();
                                } catch (CoreException e) {
                                    // skip
                                }
                            }

                        }
                        previous.delete();
                        actual.delete();
                    }
                }
            }
        }
    }

    private void swapActualAndPreviousFile() throws IOException {
        File tmp = previous;
        previous = actual;
        actual = tmp;
        actual.delete();
        previous.renameTo(File.createTempFile("astFileA", ".java"));
    }

    private void compareAstAndSaveToDb(String selectedProjectName, long revisionDateMin, long revisionDateMax,
            IFileRevision revision) throws CanceledExecutionException, SQLException {
        distiller.extractClassifiedSourceCodeChanges(previous, actual);

        if (distiller.getSourceCodeChanges() != null) {
            for (SourceCodeChange scc : distiller.getSourceCodeChanges()) {
                checkIfCancelledAndSetProgress(null);
                SingleChangeInfo info = SourceCodeChangeExtractor.extractChanges(revision, scc);

                // only method level
                if (info.getMethodName() != null && info.hasDataCorrect()) {
                    db.insertRevisionData(info, selectedProjectName, revisionDateMin, revisionDateMax);
                }
            }
        }
    }

    private void checkIfCancelledAndSetProgress(Double progress) throws CanceledExecutionException {
        if (exec != null) {
            exec.checkCanceled();

            // no progress change
            if (progress != null) {
                exec.setProgress(progress);
            }
        }
    }
}
