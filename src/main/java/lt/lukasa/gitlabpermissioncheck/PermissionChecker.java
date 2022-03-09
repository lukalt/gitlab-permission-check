package lt.lukasa.gitlabpermissioncheck;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.gitlab4j.api.Constants;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.AccessLevel;
import org.gitlab4j.api.models.Member;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Visibility;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * @author Lukas Alt
 * @since 04.07.2021
 */
public class PermissionChecker {
    @AllArgsConstructor
    @Builder
    @Getter
    public static class AccessInfo {
        private final String projectPath;
        private final AccessLevel accessLevel;
    }

    private static String buildProgressBar(int size, double progress) {
        int taken = (int)(Math.ceil(progress * size));
        StringBuilder sb = new StringBuilder();
        sb.append("⬛".repeat(Math.max(0, taken)));
        sb.append("⬜".repeat(Math.max(0, size - taken)));
        return sb.toString();
    }

    private static String capitalize(String input) {
        String lc = input.toLowerCase();
        return Character.toUpperCase(lc.charAt(0)) + lc.substring(1);
    }

    public static void main(String[] args) throws IOException {
        BufferedReader in =  new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Please enter the absolute url of your GitLab server (e.g 'https://gitlab.com' or 'https://gitlab.my-company.com'");
        System.out.print("Input: ");
        final String serverId = in.readLine();
        System.out.println("Please enter your personal access token: ");
        System.out.print("Input: ");
        final String personalAccessToken = in.readLine();

        System.out.println("Do you want to limit the projects to check? Enter a prefix for the project path or leave empty to process all: ");
        System.out.print("Input: ");
        final String prefix = in.readLine().trim();

        System.out.println("Do you want filter the projects by state? (0 = all , 1 = active projects, 2 = archived projects)");
        System.out.print("Input [default: all]:");
        final String projectState = in.readLine().trim();
        boolean activeProjects = false;
        boolean archivedProjects = false;

        switch (projectState) {
            case "1":
                activeProjects = true;
                break;
            case "2":
                archivedProjects = true;
                break;
            default:
                activeProjects = true;
                archivedProjects = true;
                break;
        }

        final String fileName = "output.html";


        Map<String, List<AccessInfo>> access = requestAllProjects(serverId, personalAccessToken, a -> {
            if(prefix.isEmpty()) {
                return true;
            }
            return a.getPathWithNamespace().toLowerCase().startsWith(prefix.toLowerCase());
        }, archivedProjects, activeProjects);

        String formattedHtml = writeHtml(serverId, access);

        File file = new File(fileName);
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.print(formattedHtml);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println(" ----------------------- ");
        System.out.println(" Result has been written to " + file.getAbsolutePath());
        System.out.println(" ----------------------- ");
        if(Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(file.toURI());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private static Map<String, List<AccessInfo>> requestAllProjects(String serverId, String personalAccessToken, Predicate<Project> filter, boolean loadArchived, boolean loadActives) {
        GitLabApi gitLabApi = new GitLabApi(serverId, personalAccessToken);
        Map<String, List<AccessInfo>> access = new ConcurrentHashMap<>();

        System.out.println("Requesting list of projects");
        try {
            List<Project> projects = new ArrayList<>();
            if (loadActives) {
                projects.addAll(gitLabApi.getProjectApi().getProjects(false, Visibility.PRIVATE, Constants.ProjectOrderBy.NAME, Constants.SortOrder.ASC, null, null, true, true, null, null));
            }
            if (loadArchived) {
                projects.addAll(gitLabApi.getProjectApi().getProjects(true, Visibility.PRIVATE, Constants.ProjectOrderBy.NAME, Constants.SortOrder.ASC, null, null, true, true, null, null));
            }
            AtomicInteger processed = new AtomicInteger();
            projects.parallelStream().forEach(project -> {
                final double fraction = processed.get() / (double) projects.size();
                System.out.print("\rProcessing projects [" + buildProgressBar(10, fraction) + "] (" + (int)Math.floor(100 * fraction) + "%) Current: " + project.getPathWithNamespace());
                if(filter.test(project)) {
                    try {
                        for (Member allMember : gitLabApi.getProjectApi().getAllMembers(project.getPathWithNamespace())) {
                            access.computeIfAbsent(allMember.getUsername(), a -> Collections.synchronizedList(new ArrayList<>())).add(AccessInfo.builder().
                                    projectPath(project.getPathWithNamespace())
                                    .accessLevel(allMember.getAccessLevel())
                                    .build());
                        }
                    } catch (GitLabApiException e) {
                        e.printStackTrace();
                    }
                }
                processed.incrementAndGet();
            });
        } catch (GitLabApiException e) {
            e.printStackTrace();
        }
        System.out.println("\rPermission check has been completed.");
        return access;
    }

    private static String writeHtml(String serverId, Map<String, List<AccessInfo>> access) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<html><head><title>GitLab Access Report</title><link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css\" rel=\"stylesheet\" integrity=\"sha384-EVSTQN3/azprG1Anm3QDgpJLIm9Nao0Yz1ztcQTwFspd3yD65VohhpuuCOmLASjC\" crossorigin=\"anonymous\">\n</head><body><div class=\"container\">\n");

        stringBuilder.append("<h2>Users:</h2>");
        stringBuilder.append("<ul>");
        access.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(s -> {
            stringBuilder.append("<li><a href=\"#user-").append(s).append("\">").append(s).append("</a></li>\n");
        });

        stringBuilder.append("</ul>");
        for (Map.Entry<String, List<AccessInfo>> e : access.entrySet()) {
            stringBuilder.append("<h2 id=\"user-").append(e.getKey()).append("\">@").append(e.getKey()).append("</h2>\n");
            stringBuilder.append("<table class=\"table table-striped\">");
            stringBuilder.append("<tr><th>Project</th><th>Access Level</th></tr>\n");
            for (AccessInfo s : e.getValue()) {
                stringBuilder.append("<tr><td><a href=\"" + serverId + "/").append(s.getProjectPath()).append("/-/project_members").append("\">")
                        .append(s.getProjectPath()).append("</a></td><td>")
                        .append(capitalize(s.getAccessLevel().name())).append("</td></tr>\n");
            }
            stringBuilder.append("</table>");
        }
        stringBuilder.append("</div></body></html>");
        return stringBuilder.toString();
    }
}
