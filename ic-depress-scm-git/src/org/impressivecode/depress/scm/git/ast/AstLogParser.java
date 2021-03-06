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
package org.impressivecode.depress.scm.git.ast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.impressivecode.depress.scm.git.GitOnlineAdapterExtendedEntry;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;

/**
 * @author Mateusz Kutyba, Wroclaw University of Technology
 */
public class AstLogParser {
    private String repoPath;
    private String packageName;
    private Git git;
    private Repository repo;
    private Map<String, GitOnlineAdapterExtendedEntry> histories;
    private AstLogParserData data = new AstLogParserData();
    private final ExecutionContext exec;

    public AstLogParser(String path, String packageName) throws SQLException, IOException, Exception {
        this(path, packageName, null);
    }

    public AstLogParser(String path, String packageName, ExecutionContext exec) throws SQLException, Exception {
        this.repoPath = path;
        this.packageName = packageName;
        if (!this.packageName.endsWith(".")) {
            this.packageName += ".";
        }
        initializeGit(repoPath);

        this.exec = exec;
    }

    public Map<String, GitOnlineAdapterExtendedEntry> getHistories() {
        return histories;
    }

    protected void initializeGit(String path) throws NoHeadException, IOException {
        checkArgument(!isNullOrEmpty(path), "Path has to be set.");
        RepositoryBuilder gitRepoBuilder = new RepositoryBuilder();
        repo = gitRepoBuilder.setGitDir(new File(path)).readEnvironment().findGitDir().build();
        git = new Git(repo);

        // Make sure path contains a git repository.
        if (!repo.getObjectDatabase().exists()) {
            throw new NoHeadException("Directory " + path + " does not look like a git repository.");
        }
    }

    public List<String> getCommitList() throws IOException, GitAPIException {
        LogCommand log = git.log();
        log.all();

        List<String> commitList = getCommitsFromLog(log);

        return commitList;
    }

    protected List<String> getCommitsFromLog(LogCommand log) throws GitAPIException, NoHeadException {
        List<String> commitList = new ArrayList<String>();

        Iterable<RevCommit> logLines = log.call();
        Iterator<RevCommit> logIterator = logLines.iterator();
        while (logIterator.hasNext()) {
            RevCommit commit = logIterator.next();
            commitList.add(commit.getId().getName());
        }

        return commitList;
    }

    public List<String> getCommitListInRange(String topCommit, String bottomCommit) throws IOException,
            GitAPIException, InvalidSettingsException {
        if (data.commitListAll == null) {
            data.commitListAll = getCommitList();
        }
        if (topCommit.startsWith("tag:")) {
            topCommit = getCommitIdForTag(topCommit.substring(4));
        }
        if (bottomCommit.startsWith("tag:")) {
            bottomCommit = getCommitIdForTag(bottomCommit.substring(4));
        }
        if ("initial".equals(bottomCommit)) {
            bottomCommit = data.commitListAll.get(data.commitListAll.size() - 1);
        }
        if ("current".equals(topCommit)) {
            topCommit = data.commitListAll.get(0);
        }

        if (!data.commitListAll.contains(topCommit)) {
            throw new InvalidSettingsException("Top commit not found.");
        }
        if (!data.commitListAll.contains(bottomCommit)) {
            throw new InvalidSettingsException("Bottom commit not found.");
        }

        List<String> commitListLimited = data.commitListAll.subList(data.commitListAll.indexOf(topCommit),
                data.commitListAll.indexOf(bottomCommit) + 1);

        return commitListLimited;
    }

    protected String getCommitIdForTag(String tag) throws IOException, GitAPIException {
        if (data.tagList == null) {
            data.tagList = getTagList();
        }
        String commitId = data.tagList.get("refs/tags/" + tag);

        return commitId;
    }

    public Map<String, String> getTagList() throws IOException, GitAPIException {
        Map<String, String> tagList = new TreeMap<String, String>();

        List<Ref> refs = git.tagList().call();
        for (Ref r : refs) {
            tagList.put(r.getName(), r.getObjectId().getName());
        }

        return tagList;
    }

    public List<String> getFilesInRevision(String commitHash) throws NoHeadException, IOException {
        return getFilesInRevision(commitHash, "*");
    }

    public List<String> getFilesInRevision(String commitHash, String extensionOnly) throws NoHeadException, IOException {
        List<String> fileList = new ArrayList<String>();

        ObjectId commitId = repo.resolve(commitHash);
        RevWalk revWalk = new RevWalk(repo);
        RevCommit revCommit = revWalk.parseCommit(commitId);
        RevTree revTree = revCommit.getTree();
        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(revTree);
        treeWalk.setRecursive(true);

        PathSuffixFilter extensionFilter = PathSuffixFilter.create(extensionOnly);
        treeWalk.setFilter(extensionFilter);

        while (treeWalk.next()) {
            if (treeWalk.getPathString().contains(packageName.replaceAll("\\.", "/"))) {
                fileList.add(treeWalk.getPathString());
            }
        }

        return fileList;
    }

    public List<String> getHistoryForFile(String filePath) throws IOException, GitAPIException {
        LogCommand log = git.log();
        log.all();
        log.addPath(filePath);

        List<String> commitList = getCommitsFromLog(log);

        return commitList;
    }

    public List<SingleChangeInfo> getFileChangesBetweenRevisions(String filePath, String commitHashPrevious,
            String commitHashActual) throws NoHeadException, CanceledExecutionException, SQLException,
            MissingObjectException, IOException {
        List<SingleChangeInfo> changeList = new ArrayList<SingleChangeInfo>();
        AstComparator comparator = new AstComparator();
        File filePrevious = getFileFromRevision(filePath, commitHashPrevious);
        if (filePrevious == null) {
            return changeList;
        }
        File fileActual = getFileFromRevision(filePath, commitHashActual);
        if (fileActual == null) {
            return changeList;
        }
        changeList = comparator.compareAstOfFiles(filePrevious, fileActual);

        filePrevious.delete();
        fileActual.delete();

        return changeList;
    }

    protected File getFileFromRevision(String filePath, String commitHash) throws MissingObjectException, IOException,
            NoHeadException {
        ObjectId commitId = repo.resolve(commitHash);
        RevWalk revWalk = new RevWalk(repo);
        RevCommit revCommit = revWalk.parseCommit(commitId);
        RevTree revTree = revCommit.getTree();

        TreeWalk treeWalk = new TreeWalk(repo);
        treeWalk.addTree(revTree);
        treeWalk.setRecursive(true);

        treeWalk.setFilter(PathFilter.create(filePath));
        if (!treeWalk.next()) {
            return null;
        }

        ObjectId objectId = treeWalk.getObjectId(0);
        ObjectLoader loader = repo.open(objectId);

        File file = File.createTempFile("AstLogParser", ".java");
        OutputStream out = new FileOutputStream(file);
        loader.copyTo(out);
        out.flush();
        out.close();

        file.deleteOnExit();

        return file;
    }

    public RevisionDetails getRevisionDetails(String commitHash) throws NoHeadException, IOException {
        ObjectId commitId = repo.resolve(commitHash);
        RevWalk revWalk = new RevWalk(repo);
        RevCommit revCommit = revWalk.parseCommit(commitId);

        RevisionDetails details = new RevisionDetails();
        return details.parse(revCommit);
    }

    private class RevisionDetails {
        public String author;
        public String revisionId;
        public String comment;
        public Date timestamp;

        private RevisionDetails parse(RevCommit revCommit) {
            author = revCommit.getAuthorIdent().getName();
            revisionId = revCommit.getName();
            comment = revCommit.getFullMessage();
            if (comment.endsWith("\n")) {
                comment = comment.substring(0, comment.length() - 1);
            }
            timestamp = revCommit.getAuthorIdent().getWhen();
            return this;
        }
    }

    public Map<String, GitOnlineAdapterExtendedEntry> getHistory(String bottomCommit, String topCommit) throws IOException,
            GitAPIException, InvalidSettingsException, CanceledExecutionException, SQLException {
        data.commitListBetweenRevisions = getCommitListInRange(topCommit, bottomCommit);
        data.commitListBetweenRevisions.remove(bottomCommit);

        histories = new HashMap<String, GitOnlineAdapterExtendedEntry>();

        List<String> fileList = getFilesInRevision(
                data.commitListBetweenRevisions.get(data.commitListBetweenRevisions.size() - 1), ".java");

        Long counter = 0l;
        for (String filePath : fileList) {
            List<String> commitList = getHistoryForFileInRangePostRelease(filePath);
            parseHistoryForFile(filePath, commitList);
            checkIfCancelledAndSetProgress(0.4d + 0.4d * ++counter / fileList.size());
        }
        return histories;
    }

    public List<String> getHistoryForFileInRangePostRelease(String filePath) throws IOException, GitAPIException,
            InvalidSettingsException {
        List<String> commitList = getHistoryForFile(filePath);
        commitList.retainAll(data.commitListBetweenRevisions);

        return commitList;
    }

    public void parseHistoryForFile(String filePath, List<String> commitList) throws MissingObjectException,
            CanceledExecutionException, SQLException, IOException, GitAPIException {
        for (String commit : commitList) {
            if (data.commitListAll == null) {
                data.commitListAll = getCommitList();
            }

            String previous = data.commitListAll.get(data.commitListAll.indexOf(commit) + 1);
            List<SingleChangeInfo> changeList = getFileChangesBetweenRevisions(filePath, previous, commit);

            RevisionDetails revisionDetails = getRevisionDetails(commit);

            for (SingleChangeInfo singleChangeInfo : changeList) {
                String methodName = singleChangeInfo.getMethodName();

                GitOnlineAdapterExtendedEntry historyEntry = new GitOnlineAdapterExtendedEntry(methodName + revisionDetails.revisionId);
                historyEntry.setMethodName(methodName);
                historyEntry.setAuthor(revisionDetails.author);
                historyEntry.setMessage(revisionDetails.comment);
                historyEntry.setDate(revisionDetails.timestamp);
                historyEntry.setCommitId(revisionDetails.revisionId);

                List<String> validChangeTypes = new ArrayList<String>();
                validChangeTypes.add("RETURN_TYPE_CHANGE");
                validChangeTypes.add("RETURN_TYPE_DELETE");
                validChangeTypes.add("RETURN_TYPE_INSERT");
                validChangeTypes.add("METHOD_RENAMING");
                validChangeTypes.add("PARAMETER_DELETE");
                validChangeTypes.add("PARAMETER_INSERT");
                validChangeTypes.add("PARAMETER_ORDERING_CHANGE");
                validChangeTypes.add("PARAMETER_RENAMING");
                validChangeTypes.add("DECREASING_ACCESSIBILITY_CHANGE");
                validChangeTypes.add("INCREASING_ACCESSIBILITY_CHANGE");
                validChangeTypes.add("PARAMETER_TYPE_CHANGE");
                validChangeTypes.add("STATEMENT_INSERT");
                validChangeTypes.add("STATEMENT_UPDATE");
                validChangeTypes.add("STATEMENT_DELETE");
                validChangeTypes.add("STATEMENT_PARENT_CHANGE");
                validChangeTypes.add("CONDITION_EXPRESSION_CHANGE");
                validChangeTypes.add("ALTERNATIVE_PART_INSERT");
                validChangeTypes.add("ALTERNATIVE_PART_DELETE");
                validChangeTypes.add("STATEMENT_ORDERING_CHANGE");

                if (validChangeTypes.contains(singleChangeInfo.getChangeType())) {
                    putHistoryEntry(historyEntry);
                }
            }
        }
    }

    protected void putHistoryEntry(GitOnlineAdapterExtendedEntry historyEntry) {
        if (histories == null) {
            histories = new HashMap<String, GitOnlineAdapterExtendedEntry>();
        }
        if (!histories.containsKey(historyEntry.getKey())) {
            histories.put(historyEntry.getKey(), historyEntry);
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
