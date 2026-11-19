def yaml = new File(basedir, 'app/target/openapi/api.yaml')
assert yaml.isFile()
assert new File(basedir, 'app/target/openapi/api.json').isFile()

// basePackages selects the controller of the dependency and excludes the controller of the module itself.
assert yaml.text.contains('/greetings/{name}:')
assert !yaml.text.contains('/local')
assert yaml.text.contains('title: IT API')

// Without basePackages the plugin scans only the module.
def api = new File(basedir, 'api/target/openapi/openapi.yaml')
assert api.text.contains('/greetings/{name}:')

assert !new File(basedir, 'skipped/target/openapi').exists()

def json = new groovy.json.JsonSlurper().parse(new File(basedir, 'app/target/openapi/api.json'))
assert json.paths['/greetings/{name}']
