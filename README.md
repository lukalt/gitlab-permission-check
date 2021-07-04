# GitLab Permission Check

GitLab does not offer an option to show all users which have access to your projects. This tool allows you to generate a report containing all members of projects you own.
For this, you only need to provide your Personal Access Token and the GitLab instance URL.
This tool is platform independant, only Java 8 or later is required.


1. Download the latest release https://github.com/lukalt/gitlab-permission-check/releases
2. Make sure that Java 8 or later is installed
3. Run `java -jar gitlab-permission-check-1.0.0.jar`
4. Enter the URL of your GitLab server, e.g. "https://gitlab.com" or "https://gitlab.mycompany.com"
5. Enter your Personal Access Token, which can be created at https://gitlab.com/-/profile/personal_access_tokens. Make sure to check the "api" scope
6. You can limit the report to projects which have a path matching a certain prefix. This is optional, if no prefix is provided, all projects the user associated to the access token has access to will be checked 
7. The report will be created now. As a result, you will find a HTML document in the `output.html` file in your current working directory. If you are using a desktop computer, the document will open automatically in your default web browser.
