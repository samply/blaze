const core = require('@actions/core');
const {exec} = require('child_process');

try {
    const tag = process.env['GITHUB_REF'].split('/').slice(2).join('-');
    const username = core.getInput('username')
    const password = core.getInput('password')
    exec('docker login -u ' + username + ' -p ' + password + ' docker.pkg.github.com', (error, stdout, stderr) => {
        if (error) {
            core.setFailed(`Error while docker login: ${error}`);
            return;
        }
        console.log(stdout);

        exec('docker pull docker.pkg.github.com/samply/blaze/main:' + tag, (error, stdout, stderr) => {
            if (error) {
                core.setFailed(`Error while docker pull: ${error}`);
                return;
            }
            console.log(stdout);

            exec('docker run --name blaze -d -p 8080:8080 -v blaze-data:/app/data docker.pkg.github.com/samply/blaze/main:' + tag, (error, stdout, stderr) => {
                if (error) {
                    core.setFailed(`Error while docker run: ${error}`);
                    return;
                }
                console.log(stdout);

                setTimeout(_ => exec('docker logs blaze', (error, stdout, stderr) => {
                    if (error) {
                        core.setFailed(`Error while docker logs blaze: ${error}`);
                        return;
                    }
                    console.log(stdout);
                }), 50000);
            });
        });
    });

} catch (error) {
    core.setFailed(error.message);
}
