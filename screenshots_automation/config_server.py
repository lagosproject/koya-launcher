import http.server
import socketserver
import json
import os
import sys

PORT = 8099

class ConfigHandler(http.server.SimpleHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/save_config':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            config = json.loads(post_data.decode('utf-8'))
            
            # Save to split_config.json
            config_path = os.path.join(os.path.dirname(__file__), 'split_config.json')
            with open(config_path, 'w') as f:
                json.dump(config, f, indent=2)
                
            print(f"\n[CONFIG SAVED] Custom split points saved: {config}")
            
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
        print(f"  URL: http://localhost:{PORT}/screenshots_automation/configurator.html")
        print(f"============================================================\n")
        print("Waiting for configurations...")
        httpd.serve_forever()
except KeyboardInterrupt:
    print("\nServer stopped.")
    sys.exit(0)
