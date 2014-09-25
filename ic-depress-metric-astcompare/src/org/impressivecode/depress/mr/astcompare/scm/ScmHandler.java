package org.impressivecode.depress.mr.astcompare.scm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.history.IFileHistory;
import org.eclipse.team.core.history.IFileHistoryProvider;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.core.history.provider.FileHistoryProvider;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;

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
public class ScmHandler {

    private final ExecutionContext exec;
    private RepositoryProvider repositoryProvider;
    String topCommit;
    String bottomCommit;
    long revisionDateMin;
    public long getRevisionDateMin() {
        return revisionDateMin;
    }

    public long getRevisionDateMax() {
        return revisionDateMax;
    }

    long revisionDateMax;

    public ScmHandler(ExecutionContext exec, RepositoryProvider repositoryProvider, String bottomCommit,
            String topCommit) {
        super();
        this.exec = exec;
        this.repositoryProvider = repositoryProvider;
        this.bottomCommit = bottomCommit;
        this.topCommit = topCommit;
    }
    
    public void convertCommitIdsToDates(IPackageFragment[] packages) throws JavaModelException, CanceledExecutionException, InvalidSettingsException {
        revisionDateMin = Long.MIN_VALUE;
        revisionDateMax = Long.MAX_VALUE;
        if (bottomCommit.trim().isEmpty() && topCommit.trim().isEmpty()) {
            return;
        }
        Hashtable<String, Long> revisionsWithTimestamps = new Hashtable<String, Long>();
        
        double progressIndex = 1.0d;
        
        for (IPackageFragment mypackage : packages) {
            checkIfCancelledAndSetProgress((progressIndex++ / packages.length) * 0.2d);
            
            if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
                for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
                    checkIfCancelledAndSetProgress(null);
                    
                    IFileRevision[] fileRevisions = getFileRevisions(unit.getResource());

                    if (fileRevisions != null && fileRevisions.length > 1) {
                        for (IFileRevision revision : fileRevisions) {
                            checkIfCancelledAndSetProgress(null);
                            
                            if (!revisionsWithTimestamps.contains(revision.getContentIdentifier())) {
                                revisionsWithTimestamps.put(revision.getContentIdentifier(), revision.getTimestamp());
                            }
                        }
                    }
                }
            }
        }
        
        if (revisionsWithTimestamps.containsKey(bottomCommit.trim())) {
            revisionDateMin = revisionsWithTimestamps.get(bottomCommit.trim());
        } else if (!bottomCommit.trim().isEmpty()) {
            throw new InvalidSettingsException("Wrong first commit ID!");
        }
        if (revisionsWithTimestamps.containsKey(topCommit.trim())) {
            revisionDateMax = revisionsWithTimestamps.get(topCommit.trim()); 
        } else if (!topCommit.trim().isEmpty()) {
            throw new InvalidSettingsException("Wrong last commit ID!");
        }
    }

    public IFileRevision[] getProperFileRevisions(IResource file) throws CanceledExecutionException {

        List<IFileRevision> revisionList = new ArrayList<IFileRevision>(100);
        IFileRevision[] allRevisions = getFileRevisions(file);
        IFileRevision initRevision = null;

        if (allRevisions != null && allRevisions.length > 1) {
            for (IFileRevision revision : allRevisions) {
                if (exec != null) {
                    exec.checkCanceled();
                }
                // find initial revision
                if (revision.getTimestamp() < revisionDateMin) {
                    if (initRevision == null) {
                        initRevision = revision;
                    }
                    if (initRevision != null && revision.getTimestamp() > initRevision.getTimestamp()) {
                        initRevision = revision;
                    }
                }

                // add revision if in range
                if (isRevisionDateInRange(revision.getTimestamp(), revisionDateMin, revisionDateMax)) {
                    revisionList.add(revision);
                }
            }
            // add initial revision
            if (initRevision != null) {
                revisionList.add(initRevision);
            }
            Collections.sort(revisionList, new FileRevisionComparable());
        }
        return revisionList.toArray(new IFileRevision[revisionList.size()]);
    }

    private IFileRevision[] getFileRevisions(IResource file) {

        IFileRevision[] revisions = null;

        IFileHistoryProvider fileHistoryProvider = repositoryProvider.getFileHistoryProvider();

        if (fileHistoryProvider == null)
            return null;

        IFileHistory iFileHistory = fileHistoryProvider.getFileHistoryFor(file,
                FileHistoryProvider.SINGLE_LINE_OF_DESCENT, null);

        if (iFileHistory != null) {
            revisions = iFileHistory.getFileRevisions();
        }

        return revisions;
    }

    private boolean isRevisionDateInRange(long revisionDate, long rangeMin, long rangeMax) {
        return revisionDate >= rangeMin && revisionDate <= rangeMax;
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
