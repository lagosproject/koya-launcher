import http.server
import socketserver
import json
import os
import sys

PORT = 8099

class ConfigHandler(http.server.SimpleHTTPRequestHandler):
    def do_POST(self):
        if self.path in ('/save_config', '/save_config_tablet'):
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            config = json.loads(post_data.decode('utf-8'))
            
            # Save to appropriate config file
            if self.path == '/save_config_tablet':
                config_file = 'split_config_tablet.json'
            else:
                config_file = 'split_config.json'
            
            config_path = os.path.join(os.path.dirname(__file__), config_file)
            with open(config_path, 'w') as f:
                json.dump(config, f, indent=2)
                
            label = "TABLET" if "tablet" in self.path else "PHONE"
            print(f"\n[CONFIG SAVED ({label})] Custom split points saved to {config_file}: {config}")
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "success"}).encode('utf-8'))
        else:
            self.send_error(404)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

# Set working directory to project root so relative URLs resolved by SimpleHTTPRequestHandler are correct
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(project_root)

handler = ConfigHandler

# Allow quick address reuse to prevent "Address already in use" errors on restart
socketserver.TCPServer.allow_reuse_address = True

try:
    with socketserver.TCPServer(("", PORT), handler) as httpd:
        print(f"\n============================================================")
        print(f"  Koya Launcher Split Configurator Server Running")
        print(f"  Phone:  http://localhost:{PORT}/screenshots_automation/configurator.html")
        print(f"  Tablet: http://localhost:{PORT}/screenshots_automation/configurator_tablet.html")
        print(f"============================================================\n")
        print("Waiting for configurations...")
        httpd.serve_forever()
except KeyboardInterrupt:
    print("\nServer stopped.")
    sys.exit(0)
