local dependencies_file = "dependencies.txt"

-- Function to execute a shell command
local function exec(cmd)
    os.execute(cmd)
end

-- Read dependencies from file
local file = io.open(dependencies_file, "r")
if file then
    for line in file:lines() do
        local pkg, ver = line:match("^(%S+)%s+(%S+).*$")
        if pkg and ver then
            -- Install each package with its version
            exec("luarocks install " .. pkg .. " " .. ver)
        end
    end
    file:close()
else
    print("Could not open file: " .. dependencies_file)
end